import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {clearGameData, savePlayer, saveSession, saveStage} from './GameBrowserStorage';
import {AUDIO_PREFERENCE_KEY, loadAudioEnabled, saveAudioEnabled, type AudioPreferenceStorage} from './AudioPreferences';

class MemoryStorage implements AudioPreferenceStorage {
    private readonly values = new Map<string, string>();

    getItem(key: string): string | null {
        return this.values.get(key) ?? null;
    }

    setItem(key: string, value: string): void {
        this.values.set(key, value);
    }
}

describe('AudioPreferences', () => {
    beforeEach(() => {
        localStorage.clear();
    });

    afterEach(() => {
        localStorage.clear();
    });

    it('defaults to enabled when the dedicated key is absent or malformed', () => {
        const storage = new MemoryStorage();

        expect(loadAudioEnabled(storage)).toBe(true);
        storage.setItem(AUDIO_PREFERENCE_KEY, 'not-a-boolean');
        expect(loadAudioEnabled(storage)).toBe(true);
    });

    it('loads stored boolean values and saves changes immediately', () => {
        const storage = new MemoryStorage();

        saveAudioEnabled(false, storage);
        expect(storage.getItem(AUDIO_PREFERENCE_KEY)).toBe('false');
        expect(loadAudioEnabled(storage)).toBe(false);

        saveAudioEnabled(true, storage);
        expect(storage.getItem(AUDIO_PREFERENCE_KEY)).toBe('true');
        expect(loadAudioEnabled(storage)).toBe(true);
    });

    it('falls back without throwing when storage access fails', () => {
        const failingStorage: AudioPreferenceStorage = {
            getItem: () => {
                throw new Error('security error');
            },
            setItem: () => {
                throw new Error('quota error');
            },
        };

        expect(() => loadAudioEnabled(failingStorage)).not.toThrow();
        expect(loadAudioEnabled(failingStorage)).toBe(true);
        expect(() => saveAudioEnabled(false, failingStorage)).not.toThrow();
    });

    it('keeps the independent preference when clearGameData removes session data', () => {
        saveSession('session-1');
        savePlayer({playerId: 'player-1', playerName: 'Player'});
        saveStage('GAMEPLAY');
        saveAudioEnabled(false);

        clearGameData();

        expect(localStorage.getItem('session_str')).toBeNull();
        expect(localStorage.getItem('player_obj')).toBeNull();
        expect(localStorage.getItem('gameStage_str')).toBeNull();
        expect(loadAudioEnabled()).toBe(false);
    });
});
