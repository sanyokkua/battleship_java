/** The dedicated browser key for the optional gameplay sound preference. */
export const AUDIO_PREFERENCE_KEY = 'battleship_audio_enabled';

/** Minimal storage port used to keep preference behavior testable and resilient. */
export interface AudioPreferenceStorage {
    getItem: (key: string) => string | null;
    setItem: (key: string, value: string) => void;
}

function getBrowserStorage(): AudioPreferenceStorage | null {
    try {
        return typeof localStorage === 'undefined' ? null : localStorage;
    } catch {
        return null;
    }
}

/** Loads the preference, defaulting to enabled for a new or unavailable profile. */
export function loadAudioEnabled(storage: AudioPreferenceStorage | null = getBrowserStorage()): boolean {
    try {
        return storage?.getItem(AUDIO_PREFERENCE_KEY) !== 'false';
    } catch {
        return true;
    }
}

/** Saves the preference immediately; storage failures remain silent no-ops. */
export function saveAudioEnabled(
    enabled: boolean,
    storage: AudioPreferenceStorage | null = getBrowserStorage(),
): void {
    try {
        storage?.setItem(AUDIO_PREFERENCE_KEY, String(enabled));
    } catch {
        // Browser storage can be unavailable or quota/security restricted.
    }
}
