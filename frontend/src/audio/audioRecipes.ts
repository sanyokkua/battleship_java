export type ToneRecipe = {
    kind: 'tone';
    waveform: 'sine';
    startTimeMs: number;
    durationMs: number;
    startFrequencyHz: number;
    endFrequencyHz: number;
    peakGain: number;
    envelope: 'decay' | 'sustain';
};

export type NoiseRecipe = {
    kind: 'noise';
    source: 'generated';
    seed: number;
    startTimeMs: number;
    durationMs: number;
    peakGain: number;
    filter?: {
        type: 'lowpass';
        frequencyHz: number;
    };
};

export type AudioRecipe = {
    durationMs: number;
    masterGain: number;
    components: readonly AudioComponent[];
};

export type AudioComponent = ToneRecipe | NoiseRecipe;

export const AUDIO_MASTER_GAIN = 0.2;

export const AUDIO_RECIPES = {
    MISS: {
        durationMs: 145,
        masterGain: AUDIO_MASTER_GAIN,
        components: [
            {
                kind: 'tone',
                waveform: 'sine',
                startTimeMs: 0,
                durationMs: 145,
                startFrequencyHz: 330,
                endFrequencyHz: 470,
                peakGain: 0.7,
                envelope: 'sustain',
            },
        ],
    },
    HIT: {
        durationMs: 130,
        masterGain: AUDIO_MASTER_GAIN,
        components: [
            {
                kind: 'noise',
                source: 'generated',
                seed: 0x484954,
                startTimeMs: 0,
                durationMs: 45,
                peakGain: 0.8,
            },
            {
                kind: 'tone',
                waveform: 'sine',
                startTimeMs: 0,
                durationMs: 130,
                startFrequencyHz: 127,
                endFrequencyHz: 92,
                peakGain: 0.65,
                envelope: 'decay',
            },
        ],
    },
    DESTROYED: {
        durationMs: 780,
        masterGain: AUDIO_MASTER_GAIN,
        components: [
            {
                kind: 'tone',
                waveform: 'sine',
                startTimeMs: 0,
                durationMs: 780,
                startFrequencyHz: 54,
                endFrequencyHz: 40,
                peakGain: 0.8,
                envelope: 'decay',
            },
            {
                kind: 'tone',
                waveform: 'sine',
                startTimeMs: 0,
                durationMs: 120,
                startFrequencyHz: 112,
                endFrequencyHz: 112,
                peakGain: 0.75,
                envelope: 'decay',
            },
            {
                kind: 'tone',
                waveform: 'sine',
                startTimeMs: 25,
                durationMs: 220,
                startFrequencyHz: 180,
                endFrequencyHz: 140,
                peakGain: 0.55,
                envelope: 'decay',
            },
            {
                kind: 'noise',
                source: 'generated',
                seed: 0x444553,
                startTimeMs: 0,
                durationMs: 780,
                peakGain: 0.55,
                filter: {type: 'lowpass', frequencyHz: 900},
            },
        ],
    },
} as const satisfies Record<'MISS' | 'HIT' | 'DESTROYED', AudioRecipe>;

/** Generates deterministic signed samples for in-memory noise buffers. */
export function generateDeterministicNoiseSamples(seed: number, length: number): Float32Array {
    const samples = new Float32Array(Math.max(0, length));
    let state = (seed >>> 0) || 1;
    for (let index = 0; index < samples.length; index++) {
        state = (Math.imul(state, 1664525) + 1013904223) >>> 0;
        samples[index] = (state / 0xffffffff) * 2 - 1;
    }
    return samples;
}
