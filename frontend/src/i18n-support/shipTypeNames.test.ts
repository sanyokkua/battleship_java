import {beforeAll, describe, expect, it} from 'vitest';
import i18n from '../i18n';
import type {ShipType} from '../logic/ApplicationTypes';
import {getShipTypeLabel} from './shipTypeNames';

const SHIP_TYPES: ShipType[] = ['PATROL_BOAT', 'SUBMARINE', 'DESTROYER', 'BATTLESHIP', 'CARRIER'];

describe('shipTypeNames', () => {
    beforeAll(async () => {
        if (!i18n.isInitialized) {
            await new Promise<void>(resolve => i18n.on('initialized', () => resolve()));
        }
    });

    describe.each(['en', 'uk'] as const)('locale=%s', locale => {
        beforeAll(async () => {
            await i18n.changeLanguage(locale);
        });

        it.each(SHIP_TYPES)('resolves a real (non-key-fallback) label for %s', shipType => {
            const label = getShipTypeLabel(shipType, i18n.t, 1);
            expect(label).not.toBe(shipType);
            expect(label).not.toMatch(/^common:shipType\./);
            expect(label.length).toBeGreaterThan(0);
        });
    });

    it('produces distinct singular vs plural English forms', async () => {
        await i18n.changeLanguage('en');
        const singular = getShipTypeLabel('SUBMARINE', i18n.t, 1);
        const plural = getShipTypeLabel('SUBMARINE', i18n.t, 5);
        expect(singular).toBe('Submarine');
        expect(plural).toBe('Submarines');
        expect(singular).not.toBe(plural);
    });

    it('resolves distinct Ukrainian plural categories for 1, 2-4, and 5+', async () => {
        await i18n.changeLanguage('uk');
        const one = getShipTypeLabel('SUBMARINE', i18n.t, 1);
        const few = getShipTypeLabel('SUBMARINE', i18n.t, 3);
        const many = getShipTypeLabel('SUBMARINE', i18n.t, 5);

        expect(one).toBe('Підводний човен');
        expect(few).toBe('Підводні човни');
        expect(many).toBe('Підводних човнів');

        expect(one).not.toBe(few);
        expect(few).not.toBe(many);
        expect(one).not.toBe(many);
    });
});
