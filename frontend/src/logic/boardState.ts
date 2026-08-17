import type {CellDto} from './ApplicationTypes';

/** Returns the ids of ships whose every visible cell has been shot. */
export function computeSunkShipIds(field: CellDto[][]): Set<string> {
    const cellsByShip = new Map<string, CellDto[]>();
    for (const row of field) {
        for (const cell of row) {
            if (cell.ship == null) continue;
            const shipId = cell.ship.shipId;
            const cells = cellsByShip.get(shipId);
            if (cells) {
                cells.push(cell);
            } else {
                cellsByShip.set(shipId, [cell]);
            }
        }
    }

    const sunk = new Set<string>();
    for (const [shipId, cells] of cellsByShip) {
        if (cells.every(cell => cell.hasShot)) {
            sunk.add(shipId);
        }
    }
    return sunk;
}

const MOAT_NEIGHBOR_OFFSETS: ReadonlyArray<{ dr: number; dc: number }> = [
    {dr: -1, dc: -1}, {dr: -1, dc: 0}, {dr: -1, dc: 1},
    {dr: 0, dc: -1}, {dr: 0, dc: 1},
    {dr: 1, dc: -1}, {dr: 1, dc: 0}, {dr: 1, dc: 1},
];

/** Returns row/column keys for unoccupied cells adjacent to the given ships. */
export function computeMoatCellKeys(field: CellDto[][], shipIds: Set<string>): Set<string> {
    const moat = new Set<string>();
    if (shipIds.size === 0) {
        return moat;
    }
    for (let row = 0; row < field.length; row++) {
        for (let col = 0; col < field[row].length; col++) {
            const ship = field[row][col].ship;
            if (ship == null || !shipIds.has(ship.shipId)) {
                continue;
            }
            for (const {dr, dc} of MOAT_NEIGHBOR_OFFSETS) {
                const targetRow = row + dr;
                const targetCol = col + dc;
                if (targetRow < 0 || targetRow >= field.length || targetCol < 0 || targetCol >= field[row].length) {
                    continue;
                }
                if (field[targetRow][targetCol].ship != null) {
                    continue;
                }
                moat.add(`${targetRow},${targetCol}`);
            }
        }
    }
    return moat;
}
