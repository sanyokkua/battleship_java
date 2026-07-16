/**
 * Formats a 0-based (row, col) cell position as a traditional Battleship coordinate label
 * (e.g. `(0, 0)` -> "A1", `(9, 9)` -> "J10") — column letter first, then 1-based row number.
 * The canonical label formula for this app; anything rendering a coordinate to the player
 * (board cell aria-labels, toasts, etc.) should go through this rather than re-deriving it.
 */
export function formatCoordinateLabel(row: number, col: number): string {
    const columnLetter = String.fromCharCode(65 + col);
    const rowNumber = row + 1;
    return `${columnLetter}${rowNumber}`;
}
