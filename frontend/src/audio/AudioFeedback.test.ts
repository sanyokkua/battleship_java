import {afterEach, describe, expect, it, vi} from 'vitest';
import {createAudioFeedback, type AudioContextLike} from './AudioFeedback';
import {generateDeterministicNoiseSamples} from './audioRecipes';

class FakeParam {
    value = 0;
    readonly calls: Array<{method: string; value: number; time: number}> = [];

    setValueAtTime(value: number, time: number) {
        this.value = value;
        this.calls.push({method: 'setValueAtTime', value, time});
    }

    linearRampToValueAtTime(value: number, time: number) {
        this.value = value;
        this.calls.push({method: 'linearRampToValueAtTime', value, time});
    }

    exponentialRampToValueAtTime(value: number, time: number) {
        this.value = value;
        this.calls.push({method: 'exponentialRampToValueAtTime', value, time});
    }
}

class FakeContext {
    state: AudioContextState = 'suspended';
    currentTime = 10;
    sampleRate = 44100;
    readonly destination = {};
    readonly oscillators: Array<{frequency: FakeParam; start: ReturnType<typeof vi.fn>; stop: ReturnType<typeof vi.fn>; disconnect: ReturnType<typeof vi.fn>}> = [];
    readonly sources: Array<{buffer: unknown; start: ReturnType<typeof vi.fn>; stop: ReturnType<typeof vi.fn>; disconnect: ReturnType<typeof vi.fn>}> = [];
    readonly gains: Array<{gain: FakeParam; disconnect: ReturnType<typeof vi.fn>}> = [];
    readonly filters: Array<{frequency: FakeParam; type: string; disconnect: ReturnType<typeof vi.fn>}> = [];
    resumeCalls = 0;
    closeCalls = 0;
    rejectResume = false;

    async resume() {
        this.resumeCalls++;
        if (this.rejectResume) throw new Error('activation rejected');
        this.state = 'running';
    }

    async close() {
        this.closeCalls++;
        this.state = 'closed';
    }

    createGain() {
        const gain = {gain: new FakeParam(), connect: vi.fn(), disconnect: vi.fn()};
        this.gains.push(gain);
        return gain;
    }

    createOscillator() {
        const oscillator = {
            frequency: new FakeParam(),
            type: 'sine',
            connect: vi.fn(),
            disconnect: vi.fn(),
            start: vi.fn(),
            stop: vi.fn(),
        };
        this.oscillators.push(oscillator);
        return oscillator;
    }

    createBufferSource() {
        const source = {
            buffer: null,
            connect: vi.fn(),
            disconnect: vi.fn(),
            start: vi.fn(),
            stop: vi.fn(),
        };
        this.sources.push(source);
        return source;
    }

    createBuffer(_channels: number, length: number, sampleRate: number) {
        const channel = new Float32Array(length);
        return {getChannelData: () => channel, duration: length / sampleRate};
    }

    createBiquadFilter() {
        const filter = {
            type: 'lowpass',
            frequency: new FakeParam(),
            connect: vi.fn(),
            disconnect: vi.fn(),
        };
        this.filters.push(filter);
        return filter;
    }
}

function contextFactory(context: FakeContext) {
    return () => context as unknown as AudioContextLike;
}

afterEach(() => {
    vi.restoreAllMocks();
});

describe('AudioFeedback', () => {
    it('observes MISS and HIT timing, frequency ramps, component gains, and master gain', () => {
        const missContext = new FakeContext();
        const miss = createAudioFeedback({contextFactory: contextFactory(missContext)});
        miss.unlockFromUserGesture();
        miss.play('MISS');

        expect(missContext.gains[0].gain.calls[0]).toMatchObject({method: 'setValueAtTime', value: 0.2, time: 10});
        expect(missContext.oscillators[0].start).toHaveBeenCalledWith(10);
        expect(missContext.oscillators[0].stop.mock.calls[0][0]).toBeCloseTo(10.145);
        expect(missContext.oscillators[0].frequency.calls).toEqual([
            {method: 'setValueAtTime', value: 330, time: 10},
            {method: 'linearRampToValueAtTime', value: 470, time: 10.145},
        ]);
        expect(missContext.gains[1].gain.calls).toEqual([
            {method: 'setValueAtTime', value: 0, time: 10},
            {method: 'linearRampToValueAtTime', value: 0.7, time: 10.006},
            {method: 'linearRampToValueAtTime', value: 0.0001, time: 10.145},
        ]);

        const hitContext = new FakeContext();
        const hit = createAudioFeedback({contextFactory: contextFactory(hitContext)});
        hit.unlockFromUserGesture();
        hit.play('HIT');

        expect(hitContext.sources[0].start).toHaveBeenCalledWith(10);
        expect(hitContext.sources[0].stop.mock.calls[0][0]).toBeCloseTo(10.045);
        expect(hitContext.oscillators[0].start).toHaveBeenCalledWith(10);
        expect(hitContext.oscillators[0].stop.mock.calls[0][0]).toBeCloseTo(10.13);
        const hitNoiseBuffer = hitContext.sources[0].buffer as {getChannelData: (channel: number) => Float32Array};
        expect(Array.from(hitNoiseBuffer.getChannelData(0))).toEqual(
            Array.from(generateDeterministicNoiseSamples(0x484954, 1985)),
        );
        expect(hitContext.oscillators[0].frequency.calls).toEqual([
            {method: 'setValueAtTime', value: 127, time: 10},
            {method: 'linearRampToValueAtTime', value: 92, time: 10.13},
        ]);
        expect(hitContext.gains[1].gain.calls).toEqual([
            {method: 'setValueAtTime', value: 0.8, time: 10},
            {method: 'exponentialRampToValueAtTime', value: 0.0001, time: 10.045},
        ]);
        expect(hitContext.gains[2].gain.calls).toEqual([
            {method: 'setValueAtTime', value: 0.65, time: 10},
            {method: 'exponentialRampToValueAtTime', value: 0.0001, time: 10.13},
        ]);
    });

    it('observes DESTROYED partials, filtered noise, and idempotent node cleanup', () => {
        const context = new FakeContext();
        const service = createAudioFeedback({contextFactory: contextFactory(context)});
        service.unlockFromUserGesture();
        service.play('DESTROYED');

        const [rumble, impact, partial] = context.oscillators;
        expect(rumble.frequency.calls).toEqual([
            {method: 'setValueAtTime', value: 54, time: 10},
            {method: 'linearRampToValueAtTime', value: 40, time: 10.78},
        ]);
        expect(impact.frequency.calls).toEqual([
            {method: 'setValueAtTime', value: 112, time: 10},
            {method: 'linearRampToValueAtTime', value: 112, time: 10.12},
        ]);
        expect(partial.frequency.calls[0]).toMatchObject({method: 'setValueAtTime', value: 180, time: 10.025});
        expect(partial.frequency.calls[1]).toMatchObject({method: 'linearRampToValueAtTime', value: 140});
        expect(partial.frequency.calls[1].time).toBeCloseTo(10.245);
        expect(rumble.start).toHaveBeenCalledWith(10);
        expect(rumble.stop.mock.calls[0][0]).toBeCloseTo(10.78);
        expect(impact.start).toHaveBeenCalledWith(10);
        expect(impact.stop.mock.calls[0][0]).toBeCloseTo(10.12);
        expect(partial.start).toHaveBeenCalledWith(10.025);
        expect(partial.stop.mock.calls[0][0]).toBeCloseTo(10.245);
        expect(context.sources).toHaveLength(1);
        expect(context.sources[0].start).toHaveBeenCalledWith(10);
        expect(context.sources[0].stop.mock.calls[0][0]).toBeCloseTo(10.78);
        expect(context.filters[0].type).toBe('lowpass');
        expect(context.filters[0].frequency.calls).toEqual([
            {method: 'setValueAtTime', value: 900, time: 10},
        ]);
        expect(context.gains[0].gain.calls[0]).toMatchObject({method: 'setValueAtTime', value: 0.2, time: 10});
        expect(context.gains.slice(1).map(({gain}) => gain.calls[0].value)).toEqual([0.8, 0.75, 0.55, 0.55]);
        expect(context.gains[4].gain.calls).toEqual([
            {method: 'setValueAtTime', value: 0.55, time: 10},
            {method: 'exponentialRampToValueAtTime', value: 0.0001, time: 10.78},
        ]);
        expect(context.sources[0].buffer).toBeTruthy();

        service.dispose();
        service.dispose();
        expect(context.oscillators.every(oscillator => oscillator.stop.mock.calls.length === 2)).toBe(true);
        expect(context.sources[0].stop.mock.calls.length).toBe(2);
        expect(context.oscillators.every(oscillator => oscillator.disconnect.mock.calls.length === 1)).toBe(true);
        expect(context.sources[0].disconnect.mock.calls.length).toBe(1);
        expect(context.gains.every(gain => gain.disconnect.mock.calls.length === 1)).toBe(true);
        expect(context.filters.every(filter => filter.disconnect.mock.calls.length === 1)).toBe(true);
        expect(context.closeCalls).toBe(1);
    });

    it('creates one context from an explicit gesture and schedules every recipe component', () => {
        const context = new FakeContext();
        const service = createAudioFeedback({contextFactory: contextFactory(context)});

        service.play('MISS');
        expect(context.oscillators).toHaveLength(0);

        service.unlockFromUserGesture();
        service.play('MISS');
        service.play('HIT');
        service.play('DESTROYED');

        expect(context.resumeCalls).toBe(1);
        expect(context.oscillators).toHaveLength(5);
        expect(context.sources).toHaveLength(2);
        expect(context.filters).toHaveLength(1);
        expect(context.oscillators.every(oscillator => oscillator.start.mock.calls.length === 1)).toBe(true);
        expect(context.sources.every(source => source.start.mock.calls.length === 1)).toBe(true);
    });

    it('keeps playback synchronous and safe when disabled or nodes fail', () => {
        const context = new FakeContext();
        const service = createAudioFeedback({contextFactory: contextFactory(context)});
        service.unlockFromUserGesture();
        service.setEnabled(false);
        service.play('DESTROYED');
        expect(context.oscillators).toHaveLength(0);

        service.setEnabled(true);
        context.createOscillator = () => {
            throw new Error('node creation failed');
        };
        expect(() => service.play('HIT')).not.toThrow();
    });

    it('treats an absent context and rejected resume as silent no-ops', async () => {
        const absent = createAudioFeedback({contextFactory: () => null});
        expect(() => absent.unlockFromUserGesture()).not.toThrow();
        expect(() => absent.play('MISS')).not.toThrow();

        const rejectedContext = new FakeContext();
        rejectedContext.rejectResume = true;
        const rejected = createAudioFeedback({contextFactory: contextFactory(rejectedContext)});
        expect(() => rejected.unlockFromUserGesture()).not.toThrow();
        await Promise.resolve();
        expect(() => rejected.play('HIT')).not.toThrow();
        expect(rejectedContext.oscillators).toHaveLength(0);
    });

    it('retries an existing context only through visibility recovery and disposes safely', () => {
        const context = new FakeContext();
        const service = createAudioFeedback({contextFactory: contextFactory(context)});
        service.unlockFromUserGesture();
        expect(context.resumeCalls).toBe(1);

        service.resumeFromVisibility?.();
        expect(context.resumeCalls).toBe(2);

        service.dispose();
        service.dispose();
        expect(context.closeCalls).toBe(1);
    });
});
