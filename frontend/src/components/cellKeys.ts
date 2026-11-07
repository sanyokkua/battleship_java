import type { components } from '../contract/generated/api';
import type { MessageKey } from '../i18n/translate';

type CellState = components['schemas']['CellState'];

export const cellKeys = {
    UNKNOWN: 'cell.unknown',
    WATER: 'cell.water',
    SHIP: 'cell.ship',
    MISS: 'cell.miss',
    HIT: 'cell.hit',
    SUNK: 'cell.sunk',
    REVEALED_WATER: 'cell.revealed_water',
} as const satisfies Record<CellState, MessageKey>;
