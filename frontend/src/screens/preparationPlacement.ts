import type {CellDto, Coordinate, ShipDirection, ShipDto} from '../logic/ApplicationTypes';

const BOARD_SIZE = 10;

/** Reason a client-side ship placement was rejected before calling the adapter; see {@link validatePlacement}. */
export type PlacementRejection = 'outOfBounds' | 'occupied' | 'tooClose' | null;

/**
 * Computes the target cells for placing a ship of `size` starting at `at` in
 * direction `dir`. Mirrors the backend/MockGameAdapter's own placement math
 * (row grows for VERTICAL, column grows for HORIZONTAL).
 */
export function computeShipCells(at: Coordinate, size: number, dir: ShipDirection): Coordinate[] {
    const cells: Coordinate[] = [];
    for (let i = 0; i < size; i++) {
        cells.push(dir === 'HORIZONTAL' ? {row: at.row, column: at.column + i} : {row: at.row + i, column: at.column});
    }
    return cells;
}

export function inBounds(c: Coordinate): boolean {
    return c.row >= 0 && c.row < BOARD_SIZE && c.column >= 0 && c.column < BOARD_SIZE;
}

/**
 * Client-side pre-validation for a prospective ship placement — mirrors the backend's
 * rules closely enough to give the player a *specific* rejection reason before making a
 * round trip (the backend only ever reports a single generic COORDINATE_INVALID for all
 * three cases). Checked in order:
 *   1. any target cell outside the 10x10 grid -> 'outOfBounds'
 *   2. any target cell already has a ship -> 'occupied'
 *   3. any target cell has isAvailable === false (no-go moat) and isn't itself one of
 *      this prospective placement's own target cells -> 'tooClose'
 * Returns null when the placement looks valid client-side (the adapter call is still
 * made and can still fail server-side as a fallback-safety edge case).
 */
export function validatePlacement(field: CellDto[][], at: Coordinate, size: number, dir: ShipDirection): PlacementRejection {
    const cells = computeShipCells(at, size, dir);

    if (cells.some((c) => !inBounds(c))) {
        return 'outOfBounds';
    }

    if (cells.some((c) => field[c.row][c.column].ship != null)) {
        return 'occupied';
    }

    const cellKeys = new Set(cells.map((c) => `${c.row}-${c.column}`));
    const tooClose = cells.some((c) => {
        const fieldCell = field[c.row][c.column];
        if (fieldCell.isAvailable) {
            return false;
        }
        // A cell that is unavailable only because it's one of this same placement's own
        // target cells isn't "too close" — this can't actually happen for an unoccupied
        // cell today (moats only clear on removal), but is kept for correctness/documentation.
        return !cellKeys.has(`${c.row}-${c.column}`) || fieldCell.ship == null;
    });
    if (tooClose) {
        return 'tooClose';
    }

    return null;
}

const NEIGHBOR_OFFSETS: ReadonlyArray<{ dr: number; dc: number }> = (() => {
    const offsets: { dr: number; dc: number }[] = [];
    for (let dr = -1; dr <= 1; dr++) {
        for (let dc = -1; dc <= 1; dc++) {
            if (dr !== 0 || dc !== 0) {
                offsets.push({dr, dc});
            }
        }
    }
    return offsets;
})();

/** Whether cell `c` is 8-directionally adjacent to any ship other than `ignoreShipId`. */
function isAdjacentToOtherShip(field: CellDto[][], c: Coordinate, ignoreShipId: string): boolean {
    for (const {dr, dc} of NEIGHBOR_OFFSETS) {
        const n = {row: c.row + dr, column: c.column + dc};
        if (!inBounds(n)) {
            continue;
        }
        const neighborShip = field[n.row][n.column].ship;
        if (neighborShip != null && neighborShip.shipId !== ignoreShipId) {
            return true;
        }
    }
    return false;
}

/**
 * Same as {@link validatePlacement}, but for checking whether `ignoreShipId` can move to a new
 * placement in place (e.g. rotating around its own bow): cells currently occupied by
 * `ignoreShipId` count as empty, and a moat cell counts as available again if it's only
 * moat-blocked because of `ignoreShipId` — i.e. it isn't 8-directionally adjacent to any
 * *other* ship's cell — mirroring what the server's moat recomputation would produce if
 * `ignoreShipId` were actually removed first.
 */
export function validatePlacementIgnoringShip(
    field: CellDto[][],
    at: Coordinate,
    size: number,
    dir: ShipDirection,
    ignoreShipId: string,
): PlacementRejection {
    const cells = computeShipCells(at, size, dir);

    if (cells.some((c) => !inBounds(c))) {
        return 'outOfBounds';
    }

    const occupiedByOther = cells.some((c) => {
        const ship = field[c.row][c.column].ship;
        return ship != null && ship.shipId !== ignoreShipId;
    });
    if (occupiedByOther) {
        return 'occupied';
    }

    const tooClose = cells.some((c) => {
        const fieldCell = field[c.row][c.column];
        if (fieldCell.ship?.shipId === ignoreShipId) {
            return false;
        }
        if (fieldCell.isAvailable) {
            return false;
        }
        return isAdjacentToOtherShip(field, c, ignoreShipId);
    });
    if (tooClose) {
        return 'tooClose';
    }

    return null;
}

/** Which of HORIZONTAL/VERTICAL are valid placements for a ship of `size` anchored at `at`. */
export function computeValidDirections(field: CellDto[][], at: Coordinate, size: number): ShipDirection[] {
    const directions: ShipDirection[] = ['HORIZONTAL', 'VERTICAL'];
    return directions.filter((dir) => validatePlacement(field, at, size, dir) === null);
}

/**
 * Remaining ships that fit at `at` in at least one orientation, each paired with which
 * orientation(s) are valid there. Drives the tap-empty-cell ship-picker popup's ship list.
 */
export function computeEligibleShips(
    field: CellDto[][],
    at: Coordinate,
    remainingShips: ShipDto[],
): { ship: ShipDto; directions: ShipDirection[] }[] {
    return remainingShips
        .map((ship) => ({ship, directions: computeValidDirections(field, at, ship.shipSize)}))
        .filter((entry) => entry.directions.length > 0);
}

/** One or more remaining ships of the same size, all interchangeable — see {@link computeGroupedEligibleShips}. */
export type GroupedShipOption = {
    shipSize: number;
    count: number;
    directions: ShipDirection[];
    representativeShipId: string;
};

/**
 * Same as {@link computeEligibleShips}, but collapses ships that share a size into a single
 * entry with a `count` — every ship of a given size has the same type name (size is a pure
 * function of ship type within an edition) and the same valid directions at a given cell (since
 * {@link computeValidDirections} depends only on size, not on which specific ship), so any one of
 * the group's ids is a correct choice to actually place. Drives the tap-empty-cell popup's
 * (grouped) ship list.
 */
export function computeGroupedEligibleShips(
    field: CellDto[][],
    at: Coordinate,
    remainingShips: ShipDto[],
): GroupedShipOption[] {
    const bySize = new Map<number, { count: number; directions: ShipDirection[]; shipId: string }>();
    for (const {ship, directions} of computeEligibleShips(field, at, remainingShips)) {
        const existing = bySize.get(ship.shipSize);
        if (existing) {
            existing.count += 1;
        } else {
            bySize.set(ship.shipSize, {count: 1, directions, shipId: ship.shipId});
        }
    }
    return [...bySize.entries()]
        .sort((a, b) => b[0] - a[0])
        .map(([shipSize, {count, directions, shipId}]) => ({
            shipSize,
            count,
            directions,
            representativeShipId: shipId,
        }));
}

export type PlacedShipPlacement = {
    at: Coordinate; // bow/origin coordinate, as computeShipCells expects
    direction: ShipDirection;
};

/**
 * Derives a placed ship's bow coordinate and current orientation from its cells on `field` —
 * feeds `computeValidRotation`, which needs both to check the *other* orientation in place.
 * Returns `null` if no cell on `field` belongs to `shipId` (e.g. it was just removed).
 */
export function derivePlacedShipPlacement(field: CellDto[][], shipId: string): PlacedShipPlacement | null {
    const cells: Coordinate[] = [];
    for (const row of field) {
        for (const cell of row) {
            if (cell.ship?.shipId === shipId) {
                cells.push({row: cell.row, column: cell.col});
            }
        }
    }
    if (cells.length === 0) {
        return null;
    }

    const rows = new Set(cells.map((c) => c.row));
    const direction: ShipDirection = rows.size === 1 ? 'HORIZONTAL' : 'VERTICAL';
    const at: Coordinate = direction === 'HORIZONTAL'
        ? {row: cells[0].row, column: Math.min(...cells.map((c) => c.column))}
        : {row: Math.min(...cells.map((c) => c.row)), column: cells[0].column};

    return {at, direction};
}

/**
 * The other orientation, if rotating ship `shipId` (size `size`, currently `currentDirection`)
 * in place around its own bow `at` is valid — otherwise `null` (rotate should be hidden/disabled).
 */
export function computeValidRotation(
    field: CellDto[][],
    shipId: string,
    at: Coordinate,
    size: number,
    currentDirection: ShipDirection,
): ShipDirection | null {
    const rotated: ShipDirection = currentDirection === 'HORIZONTAL' ? 'VERTICAL' : 'HORIZONTAL';
    return validatePlacementIgnoringShip(field, at, size, rotated, shipId) === null ? rotated : null;
}
