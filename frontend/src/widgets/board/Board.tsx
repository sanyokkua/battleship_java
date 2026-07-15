import type {CellDto} from '../../logic/ApplicationTypes';
import {BoardCell} from './BoardCell';
import './Board.css';

/**
 * Which board a `Board` renders and how its cells should be interpreted:
 * - `own` — the player's own field during gameplay (ships visible).
 * - `target` — the opponent's field during gameplay (ships hidden unless shot).
 * - `prep` — the player's own field during preparation (ships visible, ghost/block states apply).
 * - `result-own` / `result-target` — read-only post-game views of either field (ships visible).
 */
export type BoardMode = 'own' | 'target' | 'prep' | 'result-own' | 'result-target';

export type BoardProps = {
    field: CellDto[][]; // 10x10, field[row][col]
    mode: BoardMode;
    readonly?: boolean;
    onCellClick?: (row: number, col: number) => void;
    ghostCells?: Set<string>; // client-only valid-drop preview, keys as `${row}-${col}`, prep mode only
};

const COLUMN_LETTERS = Array.from({length: 10}, (_, i) => String.fromCharCode(65 + i));
const ROW_NUMBERS = Array.from({length: 10}, (_, i) => i + 1);

/**
 * Derives the set of ship IDs whose every cell has been shot, so `Board` can
 * render those ships as "sunk" rather than merely "hit". Also reused by
 * `GameplayScreen`'s opponent-shot toast to tell a plain hit from a sinking hit.
 */
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
        if (cells.every(c => c.hasShot)) {
            sunk.add(shipId);
        }
    }
    return sunk;
}

/**
 * Renders a 10x10 game board with column (A-J) and row (1-10) coordinate labels,
 * delegating each cell's visual state to `BoardCell`. Sunk-ship detection and
 * ghost-cell (prep-mode drop preview) lookup are computed once here and passed
 * down per cell.
 */
export function Board({field, mode, readonly, onCellClick, ghostCells}: BoardProps) {
    const sunkShipIds = computeSunkShipIds(field);

    return (
        <div className="board-wrap">
            <div className="coord-row">
                <span aria-hidden="true"/>
                {COLUMN_LETTERS.map(letter => (
                    <span key={letter}>{letter}</span>
                ))}
            </div>
            <div className="board-grid">
                <div className="rlabel-col">
                    {ROW_NUMBERS.map(n => (
                        <span className="rlabel" key={n}>
              {n}
            </span>
                    ))}
                </div>
                <div className={`board${readonly ? ' readonly' : ''}`}>
                    {field.map((row, rowIndex) =>
                        row.map((cell, colIndex) => {
                            const sunk = cell.ship != null && sunkShipIds.has(cell.ship.shipId);
                            const isGhost = ghostCells?.has(`${rowIndex}-${colIndex}`) ?? false;
                            return (
                                <BoardCell
                                    key={`${rowIndex}-${colIndex}`}
                                    cell={cell}
                                    mode={mode}
                                    sunk={sunk}
                                    isGhost={isGhost}
                                    readonly={readonly}
                                    onClick={onCellClick ? () => onCellClick(rowIndex, colIndex) : undefined}
                                />
                            );
                        }),
                    )}
                </div>
            </div>
        </div>
    );
}
