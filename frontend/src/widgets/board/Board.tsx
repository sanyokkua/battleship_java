import type {CellDto} from '../../logic/ApplicationTypes';
import {computeSunkShipIds} from '../../logic/boardState';
import {BoardCell} from './BoardCell';
import './Board.css';

export {computeMoatCellKeys, computeSunkShipIds} from '../../logic/boardState';

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
    highlightedCells?: Set<string>; // client-only just-shot flash, keys as `${row}-${col}`, own board only
};

const COLUMN_LETTERS = Array.from({length: 10}, (_, i) => String.fromCharCode(65 + i));
const ROW_NUMBERS = Array.from({length: 10}, (_, i) => i + 1);

/**
 * Renders a 10x10 game board with column (A-J) and row (1-10) coordinate labels,
 * delegating each cell's visual state to `BoardCell`. Sunk-ship detection and
 * ghost-cell (prep-mode drop preview) lookup are computed once here and passed
 * down per cell.
 */
export function Board({field, mode, readonly, onCellClick, ghostCells, highlightedCells}: BoardProps) {
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
                            const isHighlighted = highlightedCells?.has(`${rowIndex}-${colIndex}`) ?? false;
                            return (
                                <BoardCell
                                    key={`${rowIndex}-${colIndex}`}
                                    cell={cell}
                                    mode={mode}
                                    sunk={sunk}
                                    isGhost={isGhost}
                                    isHighlighted={isHighlighted}
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
