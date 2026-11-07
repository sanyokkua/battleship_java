import {
    Fragment,
    useEffect,
    useMemo,
    useRef,
    useState,
    useSyncExternalStore,
    type CSSProperties,
    type PointerEvent,
    type KeyboardEvent,
} from 'react';
import type { components } from '../contract/generated/api';
import type { MessageKey } from '../i18n/translate';
import { cellKeys } from './cellKeys';
import { useT } from '../i18n/useT';
import { usePreferences } from '../preferences/preferences';
import './Board.css';

type Coordinate = components['schemas']['Coordinate'];
type CellState = components['schemas']['CellState'];
export function columnLabel(index: number): string {
    let value = index + 1;
    let label = '';
    while (value > 0) {
        value--;
        label = String.fromCodePoint(65 + (value % 26)) + label;
        value = Math.floor(value / 26);
    }
    return label;
}

export function Board({
    board,
    dimensions,
    label,
    onActivate,
    onBlockedActivate,
    disabled = false,
    placementBlockedCells,
    liftedCells,
    onShipPointerDown,
    dragTarget,
    activeStates,
    pendingTarget,
    shot,
    highlightedShot,
}: Readonly<{
    board: components['schemas']['Board'];
    dimensions: components['schemas']['Ruleset']['board'];
    label: string;
    onActivate?: (coordinate: Coordinate) => void;
    onBlockedActivate?: (coordinate: Coordinate) => void;
    disabled?: boolean;
    placementBlockedCells?: Coordinate[] | undefined;
    liftedCells?: Coordinate[] | undefined;
    onShipPointerDown?: ((event: PointerEvent<HTMLButtonElement>, coordinate: Coordinate) => void) | undefined;
    dragTarget?: Coordinate | undefined;
    activeStates?: CellState[];
    pendingTarget?: Coordinate | undefined;
    shot?: components['schemas']['Shot'] | undefined;
    highlightedShot?: Coordinate | undefined;
}>) {
    const t = useT();
    const preferences = usePreferences();
    const motion = useMemo(() => globalThis.matchMedia('(prefers-reduced-motion: reduce)'), []);
    const reducedMotion = useSyncExternalStore(
        (listener) => {
            motion.addEventListener('change', listener);
            return () => {
                motion.removeEventListener('change', listener);
            };
        },
        () => motion.matches,
    );
    const emojiEnabled = preferences.emoji && !reducedMotion;
    const shotKey = shot ? `${shot.by}:${String(shot.target.rowIndex)}:${String(shot.target.columnIndex)}` : '';
    const pendingKey = pendingTarget ? `${String(pendingTarget.rowIndex)}:${String(pendingTarget.columnIndex)}` : '';
    const observedShot = useRef(shotKey);
    const observedPending = useRef(pendingKey);
    const animation = useRef<HTMLSpanElement | null>(null);
    const animationTimer = useRef<ReturnType<typeof globalThis.setTimeout> | undefined>(undefined);
    const [active, setActive] = useState(0);
    const cells = useRef<(HTMLButtonElement | null)[]>([]);
    const { rows, columns } = dimensions;
    const blockedCells = useMemo(
        () =>
            new Set(placementBlockedCells?.map((cell) => `${String(cell.rowIndex)}:${String(cell.columnIndex)}`) ?? []),
        [placementBlockedCells],
    );
    const shotResult = shot?.result;
    const shotRow = shot?.target.rowIndex;
    const shotColumn = shot?.target.columnIndex;
    const pendingRow = pendingTarget?.rowIndex;
    const pendingColumn = pendingTarget?.columnIndex;
    useEffect(() => {
        const isNewShot = observedShot.current !== shotKey;
        const isNewPending = observedPending.current !== pendingKey;
        observedShot.current = shotKey;
        observedPending.current = pendingKey;
        if (!emojiEnabled || isNewShot || (isNewPending && pendingKey !== '')) {
            animation.current?.remove();
            globalThis.clearTimeout(animationTimer.current);
        }
        if (!emojiEnabled) return;
        const miss = isNewShot && shotResult === 'MISS';
        const fire = !isNewShot && isNewPending && pendingKey !== '';
        if (!miss && !fire) return;
        const row = miss ? shotRow : pendingRow;
        const column = miss ? shotColumn : pendingColumn;
        if (row === undefined || column === undefined) return;
        // Animation is a presentation effect on the cell, never persistent game content.
        const element = document.createElement('span');
        element.className = `cell-emoji is-${miss ? 'miss' : 'fire'}`;
        element.setAttribute('aria-hidden', 'true');
        element.textContent = miss ? '💦' : '🚀';
        cells.current[row * columns + column]?.append(element);
        animation.current = element;
        animationTimer.current = globalThis.setTimeout(() => {
            element.remove();
        }, 800);
    }, [shotKey, pendingKey, emojiEnabled, shotResult, shotRow, shotColumn, pendingRow, pendingColumn, columns]);
    useEffect(
        () => () => {
            animation.current?.remove();
            globalThis.clearTimeout(animationTimer.current);
        },
        [],
    );
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
                        const lifted =
                            liftedCells?.some((cell) => cell.rowIndex === row && cell.columnIndex === column) ?? false;
                        const state = lifted ? 'WATER' : board.grid[row]?.[column];
                        if (!state) return <Fragment key={column} />;
                        const index = row * columns + column;
                        const inactive =
                            disabled || !onActivate || (activeStates !== undefined && !activeStates.includes(state));
                        const pending = pendingTarget?.rowIndex === row && pendingTarget.columnIndex === column;
                        const stateLabel = t(cellKeys[state]);
                        const blocked = state === 'WATER' && blockedCells.has(`${String(row)}:${String(column)}`);
                        const blockedLabel = blocked ? `, ${t('cell.unavailableForPlacement')}` : '';
                        return (
                            <button
                                key={column}
                                data-coordinate={`${String(row)}:${String(column)}`}
                                type="button"
                                role="gridcell"
                                className={`board-cell${pending ? ' is-pending' : ''}${dragTarget?.rowIndex === row && dragTarget.columnIndex === column ? ' is-drag-target' : ''}`}
                                onPointerDown={(event) => {
                                    if (!inactive && state === 'SHIP')
                                        onShipPointerDown?.(event, { rowIndex: row, columnIndex: column });
                                }}
                                data-state={state.toLowerCase()}
                                data-last-shot={
                                    highlightedShot?.rowIndex === row && highlightedShot.columnIndex === column
                                        ? 'true'
                                        : undefined
                                }
                                data-placement={blocked ? 'blocked' : undefined}
                                ref={(element) => {
                                    cells.current[index] = element;
                                }}
                                tabIndex={index === active ? 0 : -1}
                                aria-label={`${columnLabel(column)}${String(row + 1)}, ${stateLabel}${blockedLabel}`}
                                aria-disabled={inactive || blocked}
                                aria-busy={pending || undefined}
                                onFocus={() => {
                                    setActive(index);
                                }}
                                onKeyDown={(event) => {
                                    navigate(event, row, column);
                                }}
                                onClick={() => {
                                    if (inactive) return;
                                    if (blocked) onBlockedActivate?.({ rowIndex: row, columnIndex: column });
                                    else onActivate({ rowIndex: row, columnIndex: column });
                                }}
                            >
                                {(state === 'HIT' || state === 'SUNK') && (
                                    <span className="cell-mark" aria-hidden="true">
                                        {state === 'HIT' ? '×' : '✕'}
                                    </span>
                                )}
                            </button>
                        );
                    })}
                </div>
            ))}
        </div>
    );
}

type LegendState = 'miss' | 'hit' | 'sunk' | 'ship' | 'blocked';
const legendKeys = {
    miss: 'cell.miss',
    hit: 'cell.hit',
    sunk: 'cell.sunk',
    ship: 'cell.ship',
    blocked: 'cell.unavailableForPlacement',
} as const satisfies Record<LegendState, MessageKey>;

export function BoardLegend({ placement = false, own = false }: Readonly<{ placement?: boolean; own?: boolean }>) {
    const t = useT();
    let states: LegendState[] = ['miss', 'hit', 'sunk'];
    if (placement) states = ['ship', 'blocked'];
    else if (own) states = ['ship', ...states];
    return (
        <div className="board-legend">
            {states.map((state) => (
                <span className="board-legend-item" key={state}>
                    <span
                        className="board-cell board-legend-swatch"
                        data-state={state === 'blocked' ? 'water' : state}
                        data-placement={state === 'blocked' ? 'blocked' : undefined}
                        aria-hidden="true"
                        aria-disabled="true"
                    >
                        {(state === 'hit' || state === 'sunk') && <span className="cell-mark">×</span>}
                    </span>
                    {t(legendKeys[state])}
                </span>
            ))}
        </div>
    );
}
