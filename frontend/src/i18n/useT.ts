import { useCallback } from 'react';
import { usePreferences } from '../preferences/preferences';
import { translate, type MessageKey } from './translate';

/** Static keys are checked against the catalog; ruleset and ship ids come from the server and fall back to the raw id. */
export type TranslationKey = MessageKey | `ruleset.${string}` | `ship.${string}`;

export function useT() {
    const { locale } = usePreferences();
    return useCallback((key: TranslationKey) => translate(locale, key), [locale]);
}
