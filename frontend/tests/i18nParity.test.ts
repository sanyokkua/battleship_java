import { describe, expect, it } from 'vitest';
import { en } from '../src/i18n/en';
import { uk } from '../src/i18n/uk';
import { translate } from '../src/i18n/translate';

describe('translations', () => {
    it('keeps English and Ukrainian keys in parity', () => {
        expect(Object.keys(uk).sort()).toEqual(Object.keys(en).sort());
    });

    it('falls back to raw identifiers for unknown rulesets and ships', () => {
        expect(translate('en', 'ruleset.unlisted-ruleset')).toBe('unlisted-ruleset');
        expect(translate('en', 'ship.unlisted-ship')).toBe('unlisted-ship');
    });
});
