import type { TFunction } from 'i18next';

export function getEditionLabel(edition: string, t: TFunction): string {
    const key = edition.toLowerCase(); // "UKRAINIAN" -> "ukrainian", "MILTON_BRADLEY" -> "milton_bradley"
    return t(`common:edition.${key}.name`, edition);
}

export function getEditionDescription(edition: string, t: TFunction): string {
    const key = edition.toLowerCase();
    return t(`common:edition.${key}.description`, '');
}
