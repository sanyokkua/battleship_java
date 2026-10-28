import { afterEach, describe, expect, it, vi } from 'vitest';
import { createAudioFeedback } from '../src/feedback/audioFeedback';
import { playHaptics } from '../src/feedback/haptics';

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('optional shot feedback', () => {
    it('does not throw when AudioContext construction is denied', () => {
        vi.stubGlobal('AudioContext', function ThrowingAudioContext() {
            throw new Error('audio denied');
        });
        const feedback = createAudioFeedback();
        expect(() => {
            feedback.unlockFromUserGesture();
            feedback.play('MISS');
            feedback.dispose();
        }).not.toThrow();
    });

    it('does not throw when AudioContext resume rejects', async () => {
        const context = {
            state: 'suspended' as const,
            currentTime: 0,
            sampleRate: 44100,
            destination: {},
            resume: () => Promise.reject(new Error('audio denied')),
            close: () => Promise.resolve(),
            createGain: () => ({ gain: {}, connect: () => undefined }),
        };
        const feedback = createAudioFeedback({ contextFactory: () => context as never });
        expect(() => {
            feedback.unlockFromUserGesture();
        }).not.toThrow();
        await Promise.resolve();
        expect(() => {
            feedback.play('MISS');
        }).not.toThrow();
        feedback.dispose();
    });

    it('stops scheduled audio when sound is disabled', () => {
        const stopOscillator = vi.fn();
        const oscillator = {
            type: 'sine',
            frequency: {},
            connect: vi.fn(),
            start: vi.fn(),
            stop: stopOscillator,
        };
        const context = {
            state: 'running' as const,
            currentTime: 0,
            sampleRate: 44100,
            destination: {},
            resume: () => Promise.resolve(),
            close: () => Promise.resolve(),
            createGain: () => ({ gain: {}, connect: vi.fn(), disconnect: vi.fn() }),
            createOscillator: () => oscillator,
        };
        const feedback = createAudioFeedback({ contextFactory: () => context as never });
        feedback.unlockFromUserGesture();
        feedback.play('MISS');

        feedback.setEnabled(false);

        expect(stopOscillator).toHaveBeenNthCalledWith(2);
    });

    it('does not throw when vibration is unsupported or denied', () => {
        vi.stubGlobal('navigator', {});
        expect(() => {
            playHaptics('MISS');
        }).not.toThrow();
        vi.stubGlobal('navigator', {
            vibrate: () => {
                throw new Error('vibration denied');
            },
        });
        expect(() => {
            playHaptics('HIT');
        }).not.toThrow();
    });
});
