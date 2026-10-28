import { Fragment, useRef, useState, type CSSProperties, type PointerEvent, type KeyboardEvent } from 'react';
import type { components } from '../contract/generated/api';
import { useT } from '../i18n/useT';
import './Board.css';

type Coordinate = components['schemas']['Coordinate'];
type CellState = components['schemas']['CellState'];
const symbols: Record<CellState, string> = {
    UNKNOWN: '?',
    WATER: '≈',
    SHIP: '■',
    MISS: '•',
    HIT: '×',
    SUNK: '✕',
    REVEALED_WATER: '·',
};

export function columnLabel(index: number): string {
    let value = index + 1;
    let label = '';
    while (value > 0) {
        value--;
        label = String.fromCharCode(65 + (value % 26)) + label;
        value = Math.floor(value / 26);
    }
    return label;
}

export function Board({
    board,
    dimensions,
    label,
    onActivate,
    disabled = false,
    onShipPointerDown,
    dragTarget,
    activeStates,
    pendingTarget,
}: {
    board: components['schemas']['Board'];
    dimensions: components['schemas']['Ruleset']['board'];
    label: string;
    onActivate?: (coordinate: Coordinate) => void;
    disabled?: boolean;
    onShipPointerDown?: ((event: PointerEvent<HTMLButtonElement>, coordinate: Coordinate) => void) | undefined;
    dragTarget?: Coordinate | undefined;
    activeStates?: CellState[];
    pendingTarget?: Coordinate | undefined;
}) {
    const t = useT();
    const [active, setActive] = useState(0);
    const cells = useRef<(HTMLButtonElement | null)[]>([]);
    const { rows, columns } = dimensions;
    const navigate = (event: KeyboardEvent<HTMLButtonElement>, row: number, column: number) => {
        let nextRow = row;
        let nextColumn = column;
        switch (event.key) {
            case 'ArrowRight':
                nextColumn = Math.min(columns - 1, column + 1);
                break;
            case 'ArrowLeft':
                nextColumn = Math.max(0, column - 1);
                break;
            case 'ArrowDown':
                nextRow = Math.min(rows - 1, row + 1);
                break;
            case 'ArrowUp':
                nextRow = Math.max(0, row - 1);
                break;
            case 'Home':
                nextColumn = 0;
                if (event.ctrlKey) nextRow = 0;
                break;
            case 'End':
                nextColumn = columns - 1;
                if (event.ctrlKey) nextRow = rows - 1;
                break;
            default:
                return;
        }
        event.preventDefault();
        const next = nextRow * columns + nextColumn;
        setActive(next);
        cells.current[next]?.focus();
    };
    return (
        <div
            className="board-grid"
            role="grid"
            aria-label={label}
            aria-rowcount={rows + 1}
            aria-colcount={columns + 1}
            style={{ '--board-columns': columns } as CSSProperties}
        >
            <div className="board-row" role="row">
                <span role="columnheader" />
                {Array.from({ length: columns }, (_, column) => (
                    <span className="coord" role="columnheader" key={column}>
                        {columnLabel(column)}
                    </span>
                ))}
            </div>
            {Array.from({ length: rows }, (_, row) => (
                <div className="board-row" role="row" key={row}>
                    <span className="coord" role="rowheader">
                        {row + 1}
                    </span>
                    {Array.from({ length: columns }, (_, column) => {
                        const state = board.grid[row]?.[column];
                        if (!state) return <Fragment key={column} />;
                        const index = row * columns + column;
                        const inactive =
                            disabled || !onActivate || (activeStates !== undefined && !activeStates.includes(state));
                        const pending = pendingTarget?.rowIndex === row && pendingTarget.columnIndex === column;
                        const stateLabel = t(`cell.${state.toLowerCase()}`);
                        return (
                            <button
                                key={column}
                                type="button"
                                role="gridcell"
                                className={`board-cell${pending ? ' is-pending' : ''}${dragTarget?.rowIndex === row && dragTarget.columnIndex === column ? ' is-drag-target' : ''}`}
                                style={
                                    !inactive && state === 'SHIP' && onShipPointerDown
                                        ? { touchAction: 'none' }
                                        : undefined
                                }
                                onPointerDown={(event) => {
                                    if (!inactive && state === 'SHIP')
                                        onShipPointerDown?.(event, { rowIndex: row, columnIndex: column });
                                }}
                                data-state={state.toLowerCase()}
                                ref={(element) => {
                                    cells.current[index] = element;
                                }}
                                tabIndex={index === active ? 0 : -1}
                                aria-label={`${columnLabel(column)}${String(row + 1)}, ${stateLabel}`}
                                aria-disabled={inactive}
                                aria-busy={pending || undefined}
                                onFocus={() => {
                                    setActive(index);
                                }}
                                onKeyDown={(event) => {
                                    navigate(event, row, column);
                                }}
                                onClick={() => {
                                    if (!inactive) onActivate({ rowIndex: row, columnIndex: column });
                                }}
                            >
                                <span aria-hidden="true">{symbols[state]}</span>
                                <span className="sr-only">{stateLabel}</span>
                            </button>
                        );
                    })}
                </div>
            ))}
        </div>
    );
}
