import { beforeAll, describe, expect, it } from 'vitest';
import i18n from '../i18n';
import { getEditionDescription, getEditionLabel } from './editionNames';

const EDITIONS = ['UKRAINIAN', 'MILTON_BRADLEY'];

describe('editionNames', () => {
    beforeAll(async () => {
        if (!i18n.isInitialized) {
            await new Promise<void>(resolve => i18n.on('initialized', () => resolve()));
        }
    });

    describe.each(['en', 'uk'] as const)('locale=%s', locale => {
        beforeAll(async () => {
            await i18n.changeLanguage(locale);
        });

        it.each(EDITIONS)('resolves a real (non-key-fallback) label for edition %s', edition => {
            const label = getEditionLabel(edition, i18n.t);
            expect(label).not.toBe(edition);
            expect(label).not.toMatch(/^common:edition\./);
            expect(label.length).toBeGreaterThan(0);
        });

        it.each(EDITIONS)('resolves a real (non-key-fallback) description for edition %s', edition => {
            const description = getEditionDescription(edition, i18n.t);
            expect(description).not.toBe('');
            expect(description).not.toMatch(/^common:edition\./);
        });
    });

    it('resolves distinct English and Ukrainian labels for the same edition', async () => {
        await i18n.changeLanguage('en');
        const enLabel = getEditionLabel('UKRAINIAN', i18n.t);
        await i18n.changeLanguage('uk');
        const ukLabel = getEditionLabel('UKRAINIAN', i18n.t);
        expect(enLabel).not.toBe(ukLabel);
    });
});
