import type {TFunction} from 'i18next';
import type {ShipType} from '../logic/ApplicationTypes';

/**
 * Resolves a `ShipType` (e.g. `"SUBMARINE"`) to its localized, pluralized
 * display name via the `common:shipType.<type>` i18next key, where `<type>`
 * is the ship type lower-cased. `count` is passed through to i18next's plural
 * resolution, which for `uk` selects among the language's one/few/many forms
 * and for `en` selects between singular/plural.
 *
 * @param shipType - Ship type to look up.
 * @param t - i18next translate function, typically `i18n.t` or the `t` from `useTranslation`.
 * @param count - Quantity driving plural form selection; defaults to 1 (singular).
 * @returns The localized, pluralized ship type name.
 */
export function getShipTypeLabel(shipType: ShipType, t: TFunction, count = 1): string {
    return t(`common:shipType.${shipType.toLowerCase()}`, {count});
}
