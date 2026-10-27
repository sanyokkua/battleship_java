import { useCallback } from 'react';
import { usePreferences } from '../preferences/preferences';
import { translate } from './translate';

export function useT() {
    const { locale } = usePreferences();
    return useCallback((key: string) => translate(locale, key), [locale]);
}
