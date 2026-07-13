import type {TFunction} from 'i18next';
import type {ShipType} from '../logic/ApplicationTypes';

export function getShipTypeLabel(shipType: ShipType, t: TFunction, count = 1): string {
    return t(`common:shipType.${shipType.toLowerCase()}`, {count});
}
