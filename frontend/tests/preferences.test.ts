import { beforeEach, describe, expect, it } from 'vitest';
import { createStorageGateway } from '../src/preferences/storageGateway';
import { DEFAULT_PREFERENCES, readPreferences, writePreferences } from '../src/preferences/preferences';
import { createThemeController } from '../src/preferences/themeController';

describe('preferences storage', () => {
    beforeEach(() => {
        window.localStorage.clear();
    });

    it('round-trips preferences and defaults on corrupt data', () => {
        const storage = createStorageGateway(window.localStorage);
        const value = { ...DEFAULT_PREFERENCES, locale: 'uk' as const, theme: 'light' as const, emoji: true };
        writePreferences(storage, value);
        expect(readPreferences(storage)).toEqual(value);
        window.localStorage.setItem('battleship.preferences', '{bad json');
        expect(readPreferences(storage)).toEqual(DEFAULT_PREFERENCES);
    });

    it('uses defaults when local storage throws', () => {
        const storage = Object.create(window.localStorage) as Storage;
        storage.getItem = () => {
            throw new Error('blocked');
        };
        storage.setItem = () => {
            throw new Error('blocked');
        };
        storage.removeItem = () => {
            throw new Error('blocked');
        };
        expect(readPreferences(createStorageGateway(storage))).toEqual(DEFAULT_PREFERENCES);
        expect(() => {
            writePreferences(createStorageGateway(storage), DEFAULT_PREFERENCES);
        }).not.toThrow();
    });

    it('applies system theme changes from the colour-scheme media query', () => {
        let dark = false;
        let onChange: ((event: MediaQueryListEvent) => void) | undefined;
        const media = {
            get matches() {
                return dark;
            },
            addEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => {
                onChange = listener;
            },
            removeEventListener: () => {
                onChange = undefined;
            },
        } as unknown as MediaQueryList;
        const store = {
            get: () => ({ ...DEFAULT_PREFERENCES, theme: 'system' as const }),
            subscribe: () => () => undefined,
        };
        const controller = createThemeController(store, document.documentElement, () => media);
        expect(document.documentElement.dataset.theme).toBe('light');
        dark = true;
        onChange?.(new Event('change') as MediaQueryListEvent);
        expect(document.documentElement.dataset.theme).toBe('dark');
        controller.dispose();
    });
});
