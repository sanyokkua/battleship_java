import { describe, expect, it } from 'vitest';
import enCommon from './en/common.json';
import enScreens from './en/screens.json';
import enNotifications from './en/notifications.json';
import enErrors from './en/errors.json';
import ukCommon from './uk/common.json';
import ukScreens from './uk/screens.json';
import ukNotifications from './uk/notifications.json';
import ukErrors from './uk/errors.json';

type JsonValue = string | number | boolean | null | JsonObject | JsonValue[];
type JsonObject = { [key: string]: JsonValue };

function collectLeafKeys(obj: JsonObject, prefix = ''): Set<string> {
    const keys = new Set<string>();
    for (const [key, value] of Object.entries(obj)) {
        const path = prefix ? `${prefix}.${key}` : key;
        if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
            for (const leaf of collectLeafKeys(value as JsonObject, path)) {
                keys.add(leaf);
            }
        } else {
            keys.add(path);
        }
    }
    return keys;
}

// Ukrainian has 4 CLDR plural categories (one/few/many/other) vs English's 2 (one/other).
// A key ending in one of these plural suffixes is normalized to its base form before
// comparing across locales, so e.g. "submarine_few" (uk-only) does not falsely register
// as a key missing from "en".
const PLURAL_SUFFIXES = ['_zero', '_one', '_two', '_few', '_many', '_other'];

function normalizePluralKey(key: string): string {
    for (const suffix of PLURAL_SUFFIXES) {
        if (key.endsWith(suffix)) {
            return key.slice(0, -suffix.length);
        }
    }
    return key;
}

function assertParity(namespace: string, en: JsonObject, uk: JsonObject) {
    const enKeys = new Set([...collectLeafKeys(en)].map(normalizePluralKey));
    const ukKeys = new Set([...collectLeafKeys(uk)].map(normalizePluralKey));

    const missingInUk = [...enKeys].filter(k => !ukKeys.has(k)).sort();
    const missingInEn = [...ukKeys].filter(k => !enKeys.has(k)).sort();

    const messageParts: string[] = [];
    if (missingInUk.length > 0) {
        messageParts.push(`Keys present in en/${namespace} but missing in uk/${namespace}:\n  ${missingInUk.join('\n  ')}`);
    }
    if (missingInEn.length > 0) {
        messageParts.push(`Keys present in uk/${namespace} but missing in en/${namespace}:\n  ${missingInEn.join('\n  ')}`);
    }

    expect(messageParts.join('\n\n'), messageParts.join('\n\n')).toBe('');
}

describe('i18n locale key parity', () => {
    it('common namespace has identical key sets in en and uk', () => {
        assertParity('common', enCommon, ukCommon);
    });

    it('screens namespace has identical key sets in en and uk', () => {
        assertParity('screens', enScreens, ukScreens);
    });

    it('notifications namespace has identical key sets in en and uk', () => {
        assertParity('notifications', enNotifications, ukNotifications);
    });

    it('errors namespace has identical key sets in en and uk', () => {
        assertParity('errors', enErrors, ukErrors);
    });
});
