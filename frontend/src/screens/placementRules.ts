import type { components } from '../contract/generated/api';
type Ship = components['schemas']['Ship'];
type Coordinate = components['schemas']['Coordinate'];
type Orientation = components['schemas']['Orientation'];
type Dimensions = components['schemas']['Ruleset']['board'];

export function canPlace(
    ship: Ship,
    anchor: Coordinate,
    orientation: Orientation,
    ships: Ship[],
    dimensions: Dimensions,
    shipsMayTouch: boolean,
) {
    const others = ships.filter((other) => other.shipId !== ship.shipId).flatMap((other) => other.cells);
    return Array.from({ length: ship.length }, (_, i) => ({
        rowIndex: anchor.rowIndex + (orientation === 'VERTICAL' ? i : 0),
        columnIndex: anchor.columnIndex + (orientation === 'HORIZONTAL' ? i : 0),
    })).every(
        (cell) =>
            cell.rowIndex >= 0 &&
            cell.rowIndex < dimensions.rows &&
            cell.columnIndex >= 0 &&
            cell.columnIndex < dimensions.columns &&
            others.every((other) => {
                const dr = Math.abs(cell.rowIndex - other.rowIndex);
                const dc = Math.abs(cell.columnIndex - other.columnIndex);
                return shipsMayTouch ? dr !== 0 || dc !== 0 : dr > 1 || dc > 1;
            }),
    );
}
export function movingBlocked(
    ships: Ship[],
    moving: Ship,
    dimensions: Dimensions,
    shipsMayTouch: boolean,
): Coordinate[] {
    if (shipsMayTouch) return [];
    const otherCells = ships.filter((ship) => ship.shipId !== moving.shipId).flatMap((ship) => ship.cells);
    const occupied = new Set(otherCells.map((cell) => `${String(cell.rowIndex)}:${String(cell.columnIndex)}`));
    const result: Coordinate[] = [];
    for (let rowIndex = 0; rowIndex < dimensions.rows; rowIndex++)
        for (let columnIndex = 0; columnIndex < dimensions.columns; columnIndex++) {
            if (
                !occupied.has(`${String(rowIndex)}:${String(columnIndex)}`) &&
                otherCells.some(
                    (cell) => Math.abs(cell.rowIndex - rowIndex) <= 1 && Math.abs(cell.columnIndex - columnIndex) <= 1,
                )
            )
                result.push({ rowIndex, columnIndex });
        }
    return result;
}
