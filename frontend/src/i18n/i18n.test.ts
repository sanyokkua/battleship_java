import {afterAll, beforeAll, describe, expect, it} from 'vitest';
import i18n from './index';

describe('i18n runtime setup', () => {
    beforeAll(async () => {
        if (!i18n.isInitialized) {
            await new Promise<void>(resolve => i18n.on('initialized', () => resolve()));
        }
    });

    afterAll(async () => {
        await i18n.changeLanguage('en');
    });

    it('resolves the English nav label by default (or after switching to en)', async () => {
        await i18n.changeLanguage('en');
        expect(i18n.t('common:nav.home')).toBe('Home');
    });

    it('resolves the Ukrainian nav label after switching language to uk', async () => {
        await i18n.changeLanguage('uk');
        expect(i18n.t('common:nav.home')).toBe('Головна');
    });

    it('switches back to English after switching to uk', async () => {
        await i18n.changeLanguage('uk');
        await i18n.changeLanguage('en');
        expect(i18n.t('common:nav.home')).toBe('Home');
    });
});
