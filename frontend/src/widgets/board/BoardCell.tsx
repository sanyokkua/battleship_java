import type {CellDto} from '../../logic/ApplicationTypes';
import {formatCoordinateLabel} from '../../logic/boardCoordinates';
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

/** The visual/interactive state a single cell resolves to, driving both its CSS class and aria-label. */
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

/**
 * Resolves a cell's DTO plus its board context (mode, precomputed sunk/ghost flags)
 * into a single `ResolvedState`. In target-style modes this deliberately withholds
 * ship state for cells that haven't been shot yet, regardless of what the DTO carries,
 * so an opponent's unshot ships are never visually revealed.
 */
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

/**
 * Renders a single board cell as either a clickable `<button>` (when `onClick` is
 * provided, the cell isn't `readonly`, and it isn't blocked/already-shot) or a plain
 * `<div>` otherwise. Derives its visual state, coordinate-based aria-label (e.g. "B4, hit"),
 * and hit/sunk symbol from `resolveState`.
 */
export function BoardCell({cell, mode, sunk, isGhost, readonly, onClick}: BoardCellProps) {
    const state = resolveState(cell, mode, sunk, isGhost);
    const ariaLabel = `${formatCoordinateLabel(cell.row, cell.col)}, ${STATE_LABEL[state]}`;

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
