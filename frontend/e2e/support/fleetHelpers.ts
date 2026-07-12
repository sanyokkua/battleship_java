import type { Coordinate, ShipDirection, ShipDto } from '../../src/logic/ApplicationTypes';

/**
 * Pure, adapter-agnostic fleet/coordinate helpers shared by both the mocked-adapter
 * e2e suite (`e2e/support/mockBackdoor.ts`) and the live-server e2e suite
 * (`e2e-live/`). Nothing in this file touches Playwright's `Page`, the mock adapter,
 * or the real HTTP adapter — it only computes placements and coordinate labels from
 * plain data, so it works identically against either backend.
 */

export type FleetPlacement = {
  shipId: string;
  shipSize: number;
  direction: ShipDirection;
  at: Coordinate; // anchor cell, as passed to addShip
  cells: Coordinate[]; // full footprint (anchor + growth in `direction`)
};

/**
 * Deterministic non-colliding placement for an entire 10-ship fleet on the 10x10
 * board, generic across both editions (verified against EDITION_SHIP_SIZES in
 * MockGameAdapter.ts):
 *   - UKRAINIAN      [1,1,1,1,2,2,2,3,3,4]  (ascending order as returned by the catalog)
 *   - MILTON_BRADLEY [2,2,2,2,3,3,3,4,4,5]  (ascending order as returned by the catalog)
 *
 * `ships` is expected in ascending-size order (both editions' catalogs already come
 * back that way from getPreparationState, since MockGameAdapter builds them straight
 * off EDITION_SHIP_SIZES). Layout:
 *   - When `verticalFirst` is set, the smallest ship (ships[0], size <= 2 in both
 *     editions) is placed VERTICALLY in the board's bottom-right corner (column 9,
 *     rows [10-size, 9]) — this cell/column is never touched by any of the
 *     horizontal placements below (their widest row tops out at column 8 for
 *     Milton Bradley, column 7 for Ukrainian), and it's >= 2 rows away from the
 *     nearest horizontal ship's moat.
 *   - The rest are paired two-per-row (rows 0, 2, 4, 6, 8), both HORIZONTAL: within a
 *     row the second ship starts at `firstShipSize + 1` (one column past the first
 *     ship's 1-cell moat), and consecutive used rows are 2 apart so one row's moat
 *     (which only reaches its immediate neighbour row) never reaches the next row's
 *     ships. An odd ship count leaves a single, unpaired ship alone in the last row.
 *   - Row-pair width never exceeds 9 (Milton Bradley's largest pair, 4+1+4) or the
 *     board's 10 columns; every cell is in-bounds for both editions.
 */
export function computeFleetLayout(ships: ShipDto[], opts?: { verticalFirst?: boolean }): FleetPlacement[] {
  const placements: FleetPlacement[] = [];
  let rest = ships;

  if (opts?.verticalFirst && ships.length > 0) {
    const [vertical, ...remaining] = ships;
    const at: Coordinate = { row: 10 - vertical.shipSize, column: 9 };
    placements.push({
      shipId: vertical.shipId,
      shipSize: vertical.shipSize,
      direction: 'VERTICAL',
      at,
      cells: Array.from({ length: vertical.shipSize }, (_, i) => ({ row: at.row + i, column: at.column })),
    });
    rest = remaining;
  }

  for (let i = 0; i < rest.length; i += 2) {
    const row = (i / 2) * 2;
    placements.push(horizontalPlacement(rest[i], row, 0));

    const second = rest[i + 1];
    if (second) {
      placements.push(horizontalPlacement(second, row, rest[i].shipSize + 1));
    }
  }

  return placements;
}

function horizontalPlacement(ship: ShipDto, row: number, column: number): FleetPlacement {
  return {
    shipId: ship.shipId,
    shipSize: ship.shipSize,
    direction: 'HORIZONTAL',
    at: { row, column },
    cells: Array.from({ length: ship.shipSize }, (_, i) => ({ row, column: column + i })),
  };
}

/** "A1"-style board coordinate label for a cell, matching BoardCell.tsx's aria-label scheme. */
export function coordinateLabel(at: Coordinate): string {
  const columnLetter = String.fromCharCode(65 + at.column);
  const rowNumber = at.row + 1;
  return `${columnLetter}${rowNumber}`;
}

/** Matches a board cell's aria-label by coordinate regardless of its current state suffix (e.g. "C7, water" or "C7, hit"). */
export function coordinateLabelPattern(at: Coordinate): RegExp {
  return new RegExp(`^${coordinateLabel(at)}, `);
}
