import type {ShipType} from '../logic/ApplicationTypes';

export type EditionComposition = { type: ShipType; size: number; count: number }[];

export const EDITION_COMPOSITIONS: Record<string, EditionComposition> = {
    UKRAINIAN: [
        {type: 'PATROL_BOAT', size: 1, count: 4},
        {type: 'SUBMARINE', size: 2, count: 3},
        {type: 'DESTROYER', size: 3, count: 2},
        {type: 'BATTLESHIP', size: 4, count: 1},
    ],
    MILTON_BRADLEY: [
        {type: 'SUBMARINE', size: 2, count: 4},
        {type: 'DESTROYER', size: 3, count: 3},
        {type: 'BATTLESHIP', size: 4, count: 2},
        {type: 'CARRIER', size: 5, count: 1},
    ],
};

export function getShipTypeForSize(edition: string, shipSize: number): ShipType | undefined {
    return EDITION_COMPOSITIONS[edition]?.find(c => c.size === shipSize)?.type;
}
