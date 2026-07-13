import type {CellDto} from '../../logic/ApplicationTypes';
import type {BoardMode} from './Board';
import './Board.css';

export type BoardCellProps = {
    cell: CellDto;
    mode: BoardMode;
    sunk: boolean;
    isGhost: boolean;
    readonly?: boolean;
    onClick?: () => void;
};

type ResolvedState = 'ghost' | 'sunk' | 'hit' | 'miss' | 'ship' | 'block' | 'water';

const STATE_LABEL: Record<ResolvedState, string> = {
    ghost: 'valid drop',
    sunk: 'sunk',
    hit: 'hit',
    miss: 'miss',
    ship: 'your ship',
    block: 'blocked',
    water: 'water',
};

// Modes where a player's own ships are actually visible on the board.
const OWN_SHIP_VISIBLE_MODES: ReadonlySet<BoardMode> = new Set<BoardMode>([
    'own',
    'prep',
    'result-own',
    'result-target',
]);

function resolveState(cell: CellDto, mode: BoardMode, sunk: boolean, isGhost: boolean): ResolvedState {
    if (mode === 'prep' && isGhost) {
        return 'ghost';
    }

    // Defensive guard: in target-style modes, never visually reveal an unshot ship,
    // regardless of what the DTO carries. Only cells that have actually been shot
    // may show ship-derived state (hit/sunk) in these modes.
    const shipVisible = cell.ship != null && (cell.hasShot || OWN_SHIP_VISIBLE_MODES.has(mode));

    if (cell.hasShot && cell.ship != null) {
        return sunk ? 'sunk' : 'hit';
    }
    if (cell.hasShot && cell.ship == null) {
        return 'miss';
    }
    if (shipVisible && !cell.hasShot) {
        return 'ship';
    }
    if (mode === 'prep' && cell.isAvailable === false && cell.ship == null) {
        return 'block';
    }
    return 'water';
}

export function BoardCell({cell, mode, sunk, isGhost, readonly, onClick}: BoardCellProps) {
    const state = resolveState(cell, mode, sunk, isGhost);
    const columnLetter = String.fromCharCode(65 + cell.col);
    const rowNumber = cell.row + 1;
    const ariaLabel = `${columnLetter}${rowNumber}, ${STATE_LABEL[state]}`;

    const isBlocked = state === 'block' || (mode === 'target' && cell.hasShot);
    const isClickable = Boolean(onClick) && !readonly && !isBlocked;

    const className = `cell cell-${state}`;

    let symbol: string | null = null;
    if (state === 'hit' || state === 'sunk') {
        symbol = '✕';
    }

    if (isClickable) {
        return (
            <button type="button" className={className} aria-label={ariaLabel} onClick={onClick}>
                {symbol}
            </button>
        );
    }

    return (
        <div className={className} aria-label={ariaLabel}>
            {symbol}
        </div>
    );
}
