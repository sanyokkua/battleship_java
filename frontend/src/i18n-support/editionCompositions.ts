import type {ShipType} from '../logic/ApplicationTypes';

/**
 * The fleet composition for one game edition: an ordered list of ship groups,
 * each naming a `ShipType`, its length in cells (`size`), and how many ships
 * of that size the edition places (`count`).
 */
export type EditionComposition = { type: ShipType; size: number; count: number }[];

/**
 * Fleet composition per `GameEdition`, keyed by the backend's edition string
 * (e.g. `"UKRAINIAN"`, `"MILTON_BRADLEY"`). This is the frontend's own copy of
 * the rules the backend's `GameEditionConfiguration` implementations enforce —
 * it drives ship-tray rendering (see `NewGameScreen`) but is display-only and
 * carries no engine authority of its own.
 */
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

/**
 * Looks up which `ShipType` has the given length within an edition's fleet
 * composition.
 *
 * @param edition - Backend edition string (e.g. `"UKRAINIAN"`); unrecognized
 *   editions simply have no composition entry.
 * @param shipSize - Ship length in cells to match against `EditionComposition.size`.
 * @returns The matching `ShipType`, or `undefined` if the edition is unknown or
 *   no ship of that size exists in its composition.
 */
export function getShipTypeForSize(edition: string, shipSize: number): ShipType | undefined {
    return EDITION_COMPOSITIONS[edition]?.find(c => c.size === shipSize)?.type;
}
