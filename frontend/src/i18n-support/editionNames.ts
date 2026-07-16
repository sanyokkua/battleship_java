import type {TFunction} from 'i18next';

/**
 * Resolves a backend `GameEdition` string (e.g. `"UKRAINIAN"`, `"MILTON_BRADLEY"`)
 * to its localized display name via the `common:edition.<key>.name` i18next key,
 * where `<key>` is the edition string lower-cased.
 *
 * @param edition - Backend edition string.
 * @param t - i18next translate function, typically `i18n.t` or the `t` from `useTranslation`.
 * @returns The localized edition name, or the raw `edition` string if no translation exists.
 */
export function getEditionLabel(edition: string, t: TFunction): string {
    const key = edition.toLowerCase(); // "UKRAINIAN" -> "ukrainian", "MILTON_BRADLEY" -> "milton_bradley"
    return t(`common:edition.${key}.name`, edition);
}

/**
 * Resolves a backend `GameEdition` string to its localized short description
 * via the `common:edition.<key>.description` i18next key, where `<key>` is the
 * edition string lower-cased.
 *
 * @param edition - Backend edition string.
 * @param t - i18next translate function, typically `i18n.t` or the `t` from `useTranslation`.
 * @returns The localized description, or `''` if no translation exists.
 */
export function getEditionDescription(edition: string, t: TFunction): string {
    const key = edition.toLowerCase();
    return t(`common:edition.${key}.description`, '');
}
