import { renderHook } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import type { components } from '../src/contract/generated/api';
import { useShotFeedback } from '../src/feedback/useShotFeedback';
import { playHaptics } from '../src/feedback/haptics';
import { preferenceStore } from '../src/preferences/preferences';

vi.mock('../src/feedback/haptics', () => ({ playHaptics: vi.fn() }));
vi.mock('../src/feedback/audioFeedback', () => ({
    createAudioFeedback: () => ({
        play: vi.fn(),
        setEnabled: vi.fn(),
        unlockFromUserGesture: vi.fn(),
        resumeFromVisibility: vi.fn(),
        dispose: vi.fn(),
    }),
}));

type Shot = components['schemas']['Shot'];
const shot = (rowIndex: number): Shot => ({ by: 'OPPONENT', target: { rowIndex, columnIndex: 0 }, result: 'HIT' });

afterEach(() => {
    vi.mocked(playHaptics).mockClear();
});

// R32: feedback plays once per new shot; a shot that was already there when the screen mounted is not new.
it('does not replay feedback for an existing last shot on mount but plays a later one', () => {
    preferenceStore.update({ haptics: true });
    const { rerender } = renderHook(({ current }) => useShotFeedback(current), { initialProps: { current: shot(1) } });
    expect(playHaptics).not.toHaveBeenCalled();

    rerender({ current: shot(2) });

    expect(playHaptics).toHaveBeenCalledExactlyOnceWith('HIT');
});
