import type {CellDto} from '../../logic/ApplicationTypes';
import {BoardCell} from './BoardCell';
import './Board.css';

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

function computeSunkShipIds(field: CellDto[][]): Set<string> {
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
