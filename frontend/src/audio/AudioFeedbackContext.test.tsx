import {afterEach, describe, expect, it, vi} from 'vitest';
import {act, cleanup, fireEvent, render, screen} from '@testing-library/react';
import {StrictMode} from 'react';
import {AudioFeedbackProvider, useAudioFeedback, type AudioFeedbackPort} from './AudioFeedbackContext';
import {AUDIO_PREFERENCE_KEY, type AudioPreferenceStorage} from '../services/AudioPreferences';

class MemoryStorage implements AudioPreferenceStorage {
    private readonly values = new Map<string, string>();

    getItem(key: string): string | null {
        return this.values.get(key) ?? null;
    }

    setItem(key: string, value: string): void {
        this.values.set(key, value);
    }
}

function fakePort() {
    let enabled = true;
    const port: AudioFeedbackPort & {resumeFromVisibility: () => void} = {
        unlockFromUserGesture: vi.fn(),
        play: vi.fn(),
        isEnabled: () => enabled,
        setEnabled: vi.fn((next: boolean) => {
            enabled = next;
        }),
        dispose: vi.fn(),
        resumeFromVisibility: vi.fn<() => void>(),
    };
    return port;
}

function Probe() {
    const audio = useAudioFeedback();
    return (
        <div>
            <span data-testid="enabled">{String(audio.isEnabled())}</span>
            <button onClick={() => audio.setEnabled(!audio.isEnabled())}>toggle</button>
            <button onClick={() => audio.play('MISS')}>play</button>
        </div>
    );
}

afterEach(() => {
    cleanup();
});

describe('AudioFeedbackProvider', () => {
    it('keeps the audio service usable through React StrictMode effect probing', async () => {
        const port = fakePort();
        let disposed = false;
        port.unlockFromUserGesture = vi.fn(() => {
            if (disposed) throw new Error('audio service was disposed during StrictMode probing');
        });
        port.dispose = vi.fn(() => {
            disposed = true;
        });

        const view = render(
            <StrictMode>
                <AudioFeedbackProvider createPort={() => port}>
                    <Probe/>
                </AudioFeedbackProvider>
            </StrictMode>,
        );

        expect(() => fireEvent.pointerDown(document.body)).not.toThrow();
        expect(port.unlockFromUserGesture).toHaveBeenCalledTimes(1);

        view.unmount();
        await act(async () => {
            await Promise.resolve();
        });
        expect(port.dispose).toHaveBeenCalledTimes(1);
    });

    it('loads enabled by default, persists toggles, and exposes no audio UI itself', async () => {
        const port = fakePort();
        const storage = new MemoryStorage();
        render(
            <AudioFeedbackProvider createPort={() => port} storage={storage}>
                <Probe/>
            </AudioFeedbackProvider>,
        );

        expect(screen.getByTestId('enabled')).toHaveTextContent('true');
        expect(screen.queryByRole('button', {name: /sound/i})).not.toBeInTheDocument();
        await act(async () => fireEvent.click(screen.getByRole('button', {name: 'toggle'})));
        expect(screen.getByTestId('enabled')).toHaveTextContent('false');
        expect(port.setEnabled).toHaveBeenCalledWith(false);
    });

    it('unlocks on pointer or keyboard gestures without playing audio', () => {
        const port = fakePort();
        render(
            <AudioFeedbackProvider createPort={() => port}>
                <Probe/>
            </AudioFeedbackProvider>,
        );

        fireEvent.pointerDown(document.body);
        fireEvent.keyDown(document.body, {key: 'Enter'});
        expect(port.unlockFromUserGesture).toHaveBeenCalledTimes(2);
        expect(port.play).not.toHaveBeenCalled();
    });

    it('uses visibility recovery for an existing context and removes listeners on unmount', async () => {
        const port = fakePort();
        const view = render(
            <AudioFeedbackProvider createPort={() => port}>
                <Probe/>
            </AudioFeedbackProvider>,
        );

        fireEvent(document, new Event('visibilitychange'));
        expect(port.resumeFromVisibility).toHaveBeenCalledTimes(1);

        view.unmount();
        fireEvent.pointerDown(document.body);
        fireEvent(document, new Event('visibilitychange'));
        expect(port.unlockFromUserGesture).not.toHaveBeenCalled();
        expect(port.resumeFromVisibility).toHaveBeenCalledTimes(1);
        await act(async () => {
            await Promise.resolve();
        });
        expect(port.dispose).toHaveBeenCalledTimes(1);
    });

    it('does not dispose the audio service when only the preference changes', async () => {
        const port = fakePort();
        render(
            <AudioFeedbackProvider createPort={() => port}>
                <Probe/>
            </AudioFeedbackProvider>,
        );

        await act(async () => fireEvent.click(screen.getByRole('button', {name: 'toggle'})));
        expect(port.dispose).not.toHaveBeenCalled();
        fireEvent.pointerDown(document.body);
        expect(port.unlockFromUserGesture).toHaveBeenCalledTimes(1);
    });

    it('restores a disabled preference and suppresses playback until re-enabled', async () => {
        const port = fakePort();
        const storage = new MemoryStorage();
        storage.setItem(AUDIO_PREFERENCE_KEY, 'false');
        render(
            <AudioFeedbackProvider createPort={() => port} storage={storage}>
                <Probe/>
            </AudioFeedbackProvider>,
        );

        expect(screen.getByTestId('enabled')).toHaveTextContent('false');
        await act(async () => fireEvent.click(screen.getByRole('button', {name: 'play'})));
        expect(port.play).not.toHaveBeenCalled();
        await act(async () => fireEvent.click(screen.getByRole('button', {name: 'toggle'})));
        await act(async () => fireEvent.click(screen.getByRole('button', {name: 'play'})));
        expect(port.play).toHaveBeenCalledWith('MISS');
    });
});
