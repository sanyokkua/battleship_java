import {describe, expect, it} from 'vitest';
import {
    AUDIO_MASTER_GAIN,
    AUDIO_RECIPES,
    generateDeterministicNoiseSamples,
} from './audioRecipes';

function toneComponents(recipe: typeof AUDIO_RECIPES[keyof typeof AUDIO_RECIPES]) {
    return recipe.components.filter(component => component.kind === 'tone');
}

function noiseComponents(recipe: typeof AUDIO_RECIPES[keyof typeof AUDIO_RECIPES]) {
    return recipe.components.filter(component => component.kind === 'noise');
}

describe('audio recipes', () => {
    it('defines the three distinct durations and stays within the master gain ceiling', () => {
        expect(AUDIO_MASTER_GAIN).toBeLessThanOrEqual(0.25);
        expect(AUDIO_RECIPES.MISS.durationMs).toBeGreaterThanOrEqual(130);
        expect(AUDIO_RECIPES.MISS.durationMs).toBeLessThanOrEqual(160);
        expect(AUDIO_RECIPES.HIT.durationMs).toBeGreaterThanOrEqual(115);
        expect(AUDIO_RECIPES.HIT.durationMs).toBeLessThanOrEqual(145);
        expect(AUDIO_RECIPES.DESTROYED.durationMs).toBeGreaterThanOrEqual(700);
        expect(AUDIO_RECIPES.DESTROYED.durationMs).toBeLessThanOrEqual(860);
        expect(AUDIO_RECIPES.DESTROYED.durationMs).toBeGreaterThanOrEqual(
            3 * Math.max(AUDIO_RECIPES.MISS.durationMs, AUDIO_RECIPES.HIT.durationMs),
        );
    });

    it('defines MISS as a bounded water-drop tonal sweep', () => {
        const [tone] = toneComponents(AUDIO_RECIPES.MISS);

        expect(tone).toMatchObject({kind: 'tone', waveform: 'sine'});
        expect(Math.abs(tone.startFrequencyHz - 330)).toBeLessThanOrEqual(5);
        expect(Math.abs(tone.endFrequencyHz - 470)).toBeLessThanOrEqual(5);
        expect(tone.startFrequencyHz).toBeGreaterThanOrEqual(325);
        expect(tone.endFrequencyHz).toBeLessThanOrEqual(475);
        expect(noiseComponents(AUDIO_RECIPES.MISS)).toHaveLength(0);
    });

    it('defines HIT as generated noise layered with the descending tonal report', () => {
        const [tone] = toneComponents(AUDIO_RECIPES.HIT);
        const [noise] = noiseComponents(AUDIO_RECIPES.HIT);

        expect(Math.abs(tone.startFrequencyHz - 127)).toBeLessThanOrEqual(5);
        expect(Math.abs(tone.endFrequencyHz - 92)).toBeLessThanOrEqual(5);
        expect(noise).toMatchObject({kind: 'noise', source: 'generated', seed: 0x484954});
        expect(noise.durationMs).toBeLessThanOrEqual(AUDIO_RECIPES.HIT.durationMs);
    });

    it('defines DESTROYED with rumble, impact, partial, and filtered deterministic noise', () => {
        const tones = toneComponents(AUDIO_RECIPES.DESTROYED);
        const [rumble, impact, partial] = tones;
        const [noise] = noiseComponents(AUDIO_RECIPES.DESTROYED);

        expect(rumble).toMatchObject({startFrequencyHz: 54, endFrequencyHz: 40, envelope: 'decay'});
        expect(Math.abs(impact.startFrequencyHz - 112)).toBeLessThanOrEqual(5);
        expect(Math.abs(impact.endFrequencyHz - 112)).toBeLessThanOrEqual(5);
        expect(Math.abs(partial.startFrequencyHz - 180)).toBeLessThanOrEqual(5);
        expect(Math.abs(partial.endFrequencyHz - 140)).toBeLessThanOrEqual(5);
        expect(noise).toMatchObject({
            kind: 'noise',
            source: 'generated',
            seed: 0x444553,
            filter: {type: 'lowpass'},
        });
    });

    it('generates deterministic in-memory noise without external source references', () => {
        const first = generateDeterministicNoiseSamples(123, 32);
        const second = generateDeterministicNoiseSamples(123, 32);
        const different = generateDeterministicNoiseSamples(124, 32);

        expect(Array.from(first)).toEqual(Array.from(second));
        expect(Array.from(first)).not.toEqual(Array.from(different));
        expect(JSON.stringify(AUDIO_RECIPES)).not.toMatch(/url|file|\.wav|\.mp3|\.ogg/i);
    });

    it('keeps every component inside its recipe duration and uses the conservative master gain', () => {
        for (const recipe of Object.values(AUDIO_RECIPES)) {
            expect(recipe.masterGain).toBe(AUDIO_MASTER_GAIN);
            expect(recipe.masterGain).toBeLessThanOrEqual(0.25);
            for (const component of recipe.components) {
                expect(component.startTimeMs).toBeGreaterThanOrEqual(0);
                expect(component.durationMs).toBeGreaterThan(0);
                expect(component.startTimeMs + component.durationMs).toBeLessThanOrEqual(recipe.durationMs);
                expect(component.peakGain).toBeGreaterThan(0);
                expect(component.peakGain).toBeLessThanOrEqual(1);
            }
        }
    });
});
