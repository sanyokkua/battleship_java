import { useSyncExternalStore } from 'react';
import { createStorageGateway, type StorageGateway } from './storageGateway';

export interface Preferences {
    locale: 'en' | 'uk';
    theme: 'dark' | 'light' | 'system';
    emoji: boolean;
    sound: boolean;
    haptics: boolean;
}

export const DEFAULT_PREFERENCES: Preferences = {
    locale: 'en',
    theme: 'dark',
    emoji: false,
    sound: true,
    haptics: false,
};
const KEY = 'battleship.preferences';
const storage = createStorageGateway();

function isPreferences(value: unknown): value is Preferences {
    if (typeof value !== 'object' || value === null) return false;
    const candidate = value as Record<string, unknown>;
    return (
        (candidate.locale === 'en' || candidate.locale === 'uk') &&
        (candidate.theme === 'dark' || candidate.theme === 'light' || candidate.theme === 'system') &&
        typeof candidate.emoji === 'boolean' &&
        typeof candidate.sound === 'boolean' &&
        typeof candidate.haptics === 'boolean'
    );
}

export function readPreferences(gateway: StorageGateway = storage): Preferences {
    const result = gateway.read(KEY);
    if (!result.ok || result.value === null) return DEFAULT_PREFERENCES;
    try {
        const parsed: unknown = JSON.parse(result.value);
        return isPreferences(parsed) ? parsed : DEFAULT_PREFERENCES;
    } catch {
        return DEFAULT_PREFERENCES;
    }
}

export function writePreferences(gateway: StorageGateway, value: Preferences): void {
    gateway.write(KEY, JSON.stringify(value));
}

let current = readPreferences();
const listeners = new Set<() => void>();
export const preferenceStore = {
    get: () => current,
    subscribe: (listener: () => void) => {
        listeners.add(listener);
        return () => {
            listeners.delete(listener);
        };
    },
    update: (update: Partial<Preferences>) => {
        current = { ...current, ...update };
        writePreferences(storage, current);
        for (const listener of listeners) listener();
    },
};

window.addEventListener('storage', (event) => {
    if (event.key === KEY) {
        current = readPreferences();
        for (const listener of listeners) listener();
    }
});

export function usePreferences(): Preferences {
    return useSyncExternalStore(preferenceStore.subscribe, preferenceStore.get, () => DEFAULT_PREFERENCES);
}
