import {createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState} from 'react';
import {
    createAudioFeedback,
    type AudioFeedbackLifecyclePort,
    type AudioFeedbackOptions,
    type AudioFeedbackPort as AudioFeedbackPortContract,
} from './AudioFeedback';
import {
    loadAudioEnabled,
    saveAudioEnabled,
    type AudioPreferenceStorage,
} from '../services/AudioPreferences';

export type AudioFeedbackPort = AudioFeedbackPortContract;

type ProviderPort = AudioFeedbackLifecyclePort | (AudioFeedbackPort & {
    resumeFromVisibility?: () => void;
});

type AudioFeedbackContextValue = AudioFeedbackPort;

export type AudioFeedbackProviderProps = {
    children: ReactNode;
    storage?: AudioPreferenceStorage | null;
    audioOptions?: AudioFeedbackOptions;
    createPort?: () => ProviderPort;
};

const AudioFeedbackContext = createContext<AudioFeedbackContextValue | null>(null);

export function AudioFeedbackProvider({
    children,
    storage,
    audioOptions,
    createPort,
}: AudioFeedbackProviderProps) {
    const [port] = useState<ProviderPort>(() => createPort?.() ?? createAudioFeedback(audioOptions));
    const [enabled, setEnabledState] = useState(() => loadAudioEnabled(storage));
    const lifecycleGenerationRef = useRef(0);

    const setEnabled = useCallback((next: boolean) => {
        setEnabledState(next);
        port.setEnabled(next);
        saveAudioEnabled(next, storage);
    }, [port, storage]);

    useEffect(() => {
        port.setEnabled(enabled);
    }, [enabled, port]);

    useEffect(() => {
        const lifecycleGeneration = ++lifecycleGenerationRef.current;
        const handleGesture = () => port.unlockFromUserGesture();
        const handleVisibility = () => {
            if (document.visibilityState === 'visible') {
                port.resumeFromVisibility?.();
            }
        };
        window.addEventListener('pointerdown', handleGesture, {passive: true});
        window.addEventListener('keydown', handleGesture);
        document.addEventListener('visibilitychange', handleVisibility);

        return () => {
            window.removeEventListener('pointerdown', handleGesture);
            window.removeEventListener('keydown', handleGesture);
            document.removeEventListener('visibilitychange', handleVisibility);
            queueMicrotask(() => {
                // This ref is a lifecycle generation counter, not a DOM ref; the latest
                // value is intentionally read when the deferred cleanup runs.
                // eslint-disable-next-line react-hooks/exhaustive-deps
                if (lifecycleGenerationRef.current === lifecycleGeneration) {
                    port.dispose();
                }
            });
        };
    }, [port]);

    const value = useMemo<AudioFeedbackContextValue>(() => ({
        unlockFromUserGesture: port.unlockFromUserGesture,
        play: outcome => {
            if (enabled) {
                port.play(outcome);
            }
        },
        isEnabled: () => enabled,
        setEnabled,
        dispose: port.dispose,
    }), [enabled, port, setEnabled]);

    return (
        <AudioFeedbackContext.Provider value={value}>
            {children}
        </AudioFeedbackContext.Provider>
    );
}

export function useAudioFeedback(): AudioFeedbackPort {
    const context = useContext(AudioFeedbackContext);
    if (!context) {
        throw new Error('useAudioFeedback must be used within an AudioFeedbackProvider');
    }
    return context;
}
