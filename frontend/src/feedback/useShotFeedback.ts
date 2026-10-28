import { useCallback, useEffect, useRef } from 'react';
import type { components } from '../contract/generated/api';
import { usePreferences } from '../preferences/preferences';
import { createAudioFeedback } from './audioFeedback';
import { playHaptics } from './haptics';

type Shot = components['schemas']['Shot'] | undefined;

export function useShotFeedback(shot: Shot): () => void {
    const preferences = usePreferences();
    const audio = useRef<ReturnType<typeof createAudioFeedback> | null>(null);
    const lastShotKey = useRef<string | undefined>(undefined);

    useEffect(() => {
        const feedback = createAudioFeedback();
        audio.current = feedback;
        const visibility = () => {
            if (document.visibilityState === 'visible') feedback.resumeFromVisibility();
        };
        document.addEventListener('visibilitychange', visibility);
        return () => {
            document.removeEventListener('visibilitychange', visibility);
            feedback.dispose();
            if (audio.current === feedback) audio.current = null;
        };
    }, []);

    useEffect(() => {
        audio.current?.setEnabled(preferences.sound);
    }, [preferences.sound]);

    useEffect(() => {
        if (!shot) return;
        const key = `${shot.by}:${String(shot.target.rowIndex)}:${String(shot.target.columnIndex)}`;
        if (lastShotKey.current === key) return;
        lastShotKey.current = key;
        if (preferences.sound) {
            audio.current?.setEnabled(true);
            audio.current?.play(shot.result === 'SUNK' ? 'DESTROYED' : shot.result);
        }
        if (preferences.haptics) playHaptics(shot.result);
    }, [shot, preferences.sound, preferences.haptics]);

    return useCallback(() => {
        if (preferences.sound) audio.current?.unlockFromUserGesture();
    }, [preferences.sound]);
}
