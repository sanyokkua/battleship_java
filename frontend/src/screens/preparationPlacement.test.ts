import {describe, expect, it} from 'vitest';
import type {CellDto, ShipDto} from '../logic/ApplicationTypes';
import {
    computeEligibleShips,
    computeGroupedEligibleShips,
    computeValidDirections,
    computeValidRotation,
    derivePlacedShipPlacement,
    validatePlacement,
    validatePlacementIgnoringShip,
} from './preparationPlacement';

function emptyField(): CellDto[][] {
    const field: CellDto[][] = [];
    for (let r = 0; r < 10; r++) {
        const row: CellDto[] = [];
        for (let c = 0; c < 10; c++) {
            row.push({row: r, col: c, ship: null, hasShot: false, isAvailable: true});
        }
        field.push(row);
    }
    return field;
}

/** Places a ship directly into a test field, recomputing occupied + 8-neighbour moat cells,
 * mirroring MockGameAdapter's own `rebuildFieldFromShips` moat rule. */
function placeShip(field: CellDto[][], shipId: string, shipSize: number, cells: { row: number; col: number }[]): void {
    for (const {row, col} of cells) {
        field[row][col].ship = {shipId, shipSize};
        field[row][col].isAvailable = false;
    }
    for (const {row, col} of cells) {
        for (let dr = -1; dr <= 1; dr++) {
            for (let dc = -1; dc <= 1; dc++) {
                if (dr === 0 && dc === 0) continue;
                const nr = row + dr;
                const nc = col + dc;
                if (nr >= 0 && nr < 10 && nc >= 0 && nc < 10 && !field[nr][nc].ship) {
                    field[nr][nc].isAvailable = false;
                }
            }
        }
    }
}

describe('validatePlacement', () => {
    it('flags an occupied cell', () => {
        const field = emptyField();
        placeShip(field, 'ship-1', 1, [{row: 5, col: 5}]);
        expect(validatePlacement(field, {row: 5, column: 4}, 2, 'HORIZONTAL')).toBe('occupied');
    });

    it('flags a moat (no-go) cell as tooClose', () => {
        const field = emptyField();
        placeShip(field, 'ship-1', 1, [{row: 5, col: 5}]);
        expect(validatePlacement(field, {row: 5, column: 6}, 1, 'HORIZONTAL')).toBe('tooClose');
    });

    it('flags an out-of-bounds placement', () => {
        const field = emptyField();
        expect(validatePlacement(field, {row: 0, column: 9}, 4, 'HORIZONTAL')).toBe('outOfBounds');
    });

    it('returns null for a valid placement on an empty field', () => {
        const field = emptyField();
        expect(validatePlacement(field, {row: 0, column: 0}, 3, 'HORIZONTAL')).toBeNull();
    });
});

describe('validatePlacementIgnoringShip', () => {
    it('treats the ignored ship\'s own cells as empty, allowing it to "rotate through" itself', () => {
        const field = emptyField();
        placeShip(field, 'ship-1', 3, [{row: 5, col: 4}, {row: 5, col: 5}, {row: 5, col: 6}]);

        // Rotating ship-1 to VERTICAL at the same bow (5,4) passes through (6,4) and (7,4),
        // which are empty water far from any other ship — should be valid once ship-1's own
        // horizontal cells (5,5) and (5,6) are excluded from the occupied/moat check.
        const result = validatePlacementIgnoringShip(field, {row: 5, column: 4}, 3, 'VERTICAL', 'ship-1');
        expect(result).toBeNull();
    });

    it('still rejects a cell occupied by a DIFFERENT ship', () => {
        const field = emptyField();
        placeShip(field, 'ship-1', 1, [{row: 0, col: 0}]);
        placeShip(field, 'ship-2', 1, [{row: 0, col: 4}]);

        const result = validatePlacementIgnoringShip(field, {row: 0, column: 4}, 1, 'HORIZONTAL', 'ship-1');
        expect(result).toBe('occupied');
    });

    it('still rejects a moat cell caused by a DIFFERENT ship', () => {
        const field = emptyField();
        placeShip(field, 'ship-1', 1, [{row: 0, col: 0}]);
        placeShip(field, 'ship-2', 1, [{row: 5, col: 5}]);

        // (5,6) is moat-blocked only by ship-2, not ship-1 — ignoring ship-1 must not clear it.
        const result = validatePlacementIgnoringShip(field, {row: 5, column: 6}, 1, 'HORIZONTAL', 'ship-1');
        expect(result).toBe('tooClose');
    });

    it('treats a moat cell caused ONLY by the ignored ship as available again', () => {
        const field = emptyField();
        placeShip(field, 'ship-1', 1, [{row: 5, col: 5}]);

        // (5,6) is moat-blocked only by ship-1 (no other ship nearby) — should clear once
        // ship-1 is the ignored ship, since removing it would lift that moat too.
        const result = validatePlacementIgnoringShip(field, {row: 5, column: 6}, 1, 'HORIZONTAL', 'ship-1');
        expect(result).toBeNull();
    });
});

describe('computeValidDirections', () => {
    it('returns both directions when either orientation fits', () => {
        const field = emptyField();
        expect(computeValidDirections(field, {row: 0, column: 0}, 2)).toEqual(['HORIZONTAL', 'VERTICAL']);
    });

    it('returns only the orientation that fits near an edge', () => {
        const field = emptyField();
        // At column 9, a size-3 ship can't grow rightward (HORIZONTAL) but can grow downward.
        expect(computeValidDirections(field, {row: 0, column: 9}, 3)).toEqual(['VERTICAL']);
    });

    it('returns an empty array when neither orientation fits', () => {
        const field = emptyField();
        placeShip(field, 'blocker', 1, [{row: 0, col: 1}]);
        // Bow (0,0), size 3: HORIZONTAL hits the blocker at (0,1); VERTICAL only has 10 rows
        // of room so it fits fine unless also blocked — block (1,0) too to rule both out.
        placeShip(field, 'blocker2', 1, [{row: 1, col: 0}]);
        expect(computeValidDirections(field, {row: 0, column: 0}, 3)).toEqual([]);
    });
});

describe('computeEligibleShips', () => {
    const ships: ShipDto[] = [
        {shipId: 'patrol', shipSize: 1},
        {shipId: 'destroyer', shipSize: 3},
        {shipId: 'carrier', shipSize: 5},
    ];

    it('filters out ships that fit in neither orientation at this cell', () => {
        const field = emptyField();
        // Near the bottom-right corner, only the size-1 ship fits.
        const result = computeEligibleShips(field, {row: 9, column: 9}, ships);
        expect(result.map((r) => r.ship.shipId)).toEqual(['patrol']);
        expect(result[0].directions).toEqual(['HORIZONTAL', 'VERTICAL']);
    });

    it('pairs each eligible ship with only its valid directions', () => {
        const field = emptyField();
        const result = computeEligibleShips(field, {row: 0, column: 8}, ships);
        const destroyer = result.find((r) => r.ship.shipId === 'destroyer');
        // size-3 at column 8: HORIZONTAL would need columns 8-10 (out of bounds), VERTICAL fits.
        expect(destroyer?.directions).toEqual(['VERTICAL']);
    });

    it('returns an empty list when nothing fits', () => {
        const field = emptyField();
        for (let c = 0; c < 10; c++) {
            if (c !== 5) placeShip(field, `blocker-${c}`, 1, [{row: 0, col: c}]);
        }
        const result = computeEligibleShips(field, {row: 0, column: 5}, [{shipId: 'destroyer', shipSize: 3}]);
        expect(result).toEqual([]);
    });
});

describe('computeGroupedEligibleShips', () => {
    it('collapses multiple same-size ships into one entry with the correct count', () => {
        const field = emptyField();
        const ships: ShipDto[] = [
            {shipId: 'patrol-1', shipSize: 1},
            {shipId: 'patrol-2', shipSize: 1},
            {shipId: 'patrol-3', shipSize: 1},
        ];
        const result = computeGroupedEligibleShips(field, {row: 0, column: 0}, ships);
        expect(result).toEqual([
            {shipSize: 1, count: 3, directions: ['HORIZONTAL', 'VERTICAL'], representativeShipId: expect.any(String)},
        ]);
        expect(ships.some((s) => s.shipId === result[0].representativeShipId)).toBe(true);
    });

    it('keeps distinct sizes as separate entries, sorted descending by size', () => {
        const field = emptyField();
        const ships: ShipDto[] = [
            {shipId: 'patrol', shipSize: 1},
            {shipId: 'destroyer', shipSize: 3},
            {shipId: 'submarine-1', shipSize: 2},
            {shipId: 'submarine-2', shipSize: 2},
        ];
        const result = computeGroupedEligibleShips(field, {row: 0, column: 0}, ships);
        expect(result.map((r) => ({shipSize: r.shipSize, count: r.count}))).toEqual([
            {shipSize: 3, count: 1},
            {shipSize: 2, count: 2},
            {shipSize: 1, count: 1},
        ]);
    });

    it('returns an empty array when nothing fits', () => {
        const field = emptyField();
        for (let c = 0; c < 10; c++) {
            if (c !== 5) placeShip(field, `blocker-${c}`, 1, [{row: 0, col: c}]);
        }
        const result = computeGroupedEligibleShips(field, {row: 0, column: 5}, [
            {shipId: 'destroyer', shipSize: 3},
        ]);
        expect(result).toEqual([]);
    });

    it("a group's directions match what computeValidDirections returns for that size (interchangeability holds)", () => {
        const field = emptyField();
        const ships: ShipDto[] = [
            {shipId: 'destroyer-1', shipSize: 3},
            {shipId: 'destroyer-2', shipSize: 3},
        ];
        const at = {row: 0, column: 8};
        const result = computeGroupedEligibleShips(field, at, ships);
        expect(result).toEqual([
            {
                shipSize: 3,
                count: 2,
                directions: computeValidDirections(field, at, 3),
                representativeShipId: expect.any(String)
            },
        ]);
    });
});

describe('derivePlacedShipPlacement', () => {
    it('derives the bow and HORIZONTAL direction for a horizontally placed ship', () => {
        const field = emptyField();
        placeShip(field, 'ship-1', 3, [{row: 5, col: 4}, {row: 5, col: 5}, {row: 5, col: 6}]);

        expect(derivePlacedShipPlacement(field, 'ship-1')).toEqual({
            at: {row: 5, column: 4},
            direction: 'HORIZONTAL',
        });
    });

    it('derives the bow and VERTICAL direction for a vertically placed ship', () => {
        const field = emptyField();
        placeShip(field, 'ship-1', 3, [{row: 2, col: 7}, {row: 3, col: 7}, {row: 4, col: 7}]);

        expect(derivePlacedShipPlacement(field, 'ship-1')).toEqual({
            at: {row: 2, column: 7},
            direction: 'VERTICAL',
        });
    });

    it('returns null when no cell on the field belongs to the given shipId', () => {
        const field = emptyField();
        expect(derivePlacedShipPlacement(field, 'nonexistent')).toBeNull();
    });
});

describe('computeValidRotation', () => {
    it('returns the other direction when rotation in place is valid', () => {
        const field = emptyField();
        placeShip(field, 'ship-1', 3, [{row: 5, col: 4}, {row: 5, col: 5}, {row: 5, col: 6}]);

        const result = computeValidRotation(field, 'ship-1', {row: 5, column: 4}, 3, 'HORIZONTAL');
        expect(result).toBe('VERTICAL');
    });

    it('returns null when rotation would go out of bounds', () => {
        const field = emptyField();
        placeShip(field, 'ship-1', 4, [{row: 9, col: 0}, {row: 9, col: 1}, {row: 9, col: 2}, {row: 9, col: 3}]);

        // Bow at (9,0): rotating to VERTICAL would need rows 9-12, off the board.
        const result = computeValidRotation(field, 'ship-1', {row: 9, column: 0}, 4, 'HORIZONTAL');
        expect(result).toBeNull();
    });

    it('returns null when rotation would collide with another ship', () => {
        const field = emptyField();
        placeShip(field, 'ship-1', 2, [{row: 0, col: 0}, {row: 0, col: 1}]);
        // ship-2 sits directly on (1,0), the second cell a VERTICAL rotation of ship-1
        // (bow (0,0), size 2) would need.
        placeShip(field, 'ship-2', 1, [{row: 1, col: 0}]);

        const result = computeValidRotation(field, 'ship-1', {row: 0, column: 0}, 2, 'HORIZONTAL');
        expect(result).toBeNull();
    });
});
