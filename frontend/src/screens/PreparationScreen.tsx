import {useEffect, useMemo, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate} from 'react-router-dom';
import {useSessionGuard} from '../hooks/useSessionGuard';
import {usePreparation} from '../hooks/usePreparation';
import {usePolling} from '../hooks/usePolling';
import {useGameAdapter} from '../adapters/GameAdapterContext';
import type {GameAdapterError} from '../adapters/AdapterErrors';
import {saveStage} from '../services/GameBrowserStorage';
import {getShipTypeLabel} from '../i18n-support/shipTypeNames';
import type {CellDto, Coordinate, ShipDirection, ShipType} from '../logic/ApplicationTypes';
import {Board} from '../widgets/board/Board';
import {Legend} from '../widgets/board/Legend';
import {LoadingView} from '../widgets/layout/LoadingView';
import {useNotify} from '../widgets/feedback/useNotify';
import {useToastContext} from '../widgets/feedback/ToastContext';
import {resolveErrorMessageKey} from '../widgets/feedback/errorMapping';
import {Button} from '../design/components/Button/Button';
import {Pill} from '../design/components/Pill/Pill';
import {DirectionToggle} from '../widgets/preparation/DirectionToggle';
import {ShipTray} from '../widgets/preparation/ShipTray';
import type {ShipItemData} from '../widgets/preparation/ShipItem';
import './PreparationScreen.css';

// Ship type from size is globally unambiguous (verified against both editions — see
// ticket notes): size 1 only exists in Ukrainian, size 5 only in Milton Bradley, and
// sizes 2-4 map identically wherever the two editions overlap. No edition lookup needed.
const SIZE_TO_TYPE: Record<number, ShipType> = {
    1: 'PATROL_BOAT',
    2: 'SUBMARINE',
    3: 'DESTROYER',
    4: 'BATTLESHIP',
    5: 'CARRIER',
};

// Stages that mean the post-ready wait is still ongoing.
const STILL_PREPARING_STAGES = new Set(['PREPARATION']);

type PlacedShipInfo = {
    shipId: string;
    shipSize: number;
};

function derivePlacedShips(field: CellDto[][]): PlacedShipInfo[] {
    const bySize = new Map<string, number>();
    for (const row of field) {
        for (const cell of row) {
            if (cell.ship != null && !bySize.has(cell.ship.shipId)) {
                bySize.set(cell.ship.shipId, cell.ship.shipSize);
            }
        }
    }
    return Array.from(bySize.entries()).map(([shipId, shipSize]) => ({shipId, shipSize}));
}

function findCellForShip(field: CellDto[][], shipId: string): CellDto | null {
    for (const row of field) {
        for (const cell of row) {
            if (cell.ship?.shipId === shipId) {
                return cell;
            }
        }
    }
    return null;
}

/**
 * Computes the target cells for placing a ship of `size` starting at `at` in
 * direction `dir`. Mirrors the backend/MockGameAdapter's own placement math
 * (row grows for VERTICAL, column grows for HORIZONTAL).
 */
function computeShipCells(at: Coordinate, size: number, dir: ShipDirection): Coordinate[] {
    const cells: Coordinate[] = [];
    for (let i = 0; i < size; i++) {
        cells.push(dir === 'HORIZONTAL' ? {row: at.row, column: at.column + i} : {row: at.row + i, column: at.column});
    }
    return cells;
}

function inBounds(c: Coordinate): boolean {
    return c.row >= 0 && c.row < 10 && c.column >= 0 && c.column < 10;
}

export type PlacementRejection = 'outOfBounds' | 'occupied' | 'tooClose' | null;

/**
 * Client-side pre-validation for a prospective ship placement — mirrors the backend's
 * rules closely enough to give the player a *specific* rejection reason before making a
 * round trip (the backend only ever reports a single generic COORDINATE_INVALID for all
 * three cases). Checked in order:
 *   1. any target cell outside the 10x10 grid -> 'outOfBounds'
 *   2. any target cell already has a ship -> 'occupied'
 *   3. any target cell has isAvailable === false (no-go moat) and isn't itself one of
 *      this prospective placement's own target cells -> 'tooClose'
 * Returns null when the placement looks valid client-side (the adapter call is still
 * made and can still fail server-side as a fallback-safety edge case).
 */
export function validatePlacement(field: CellDto[][], at: Coordinate, size: number, dir: ShipDirection): PlacementRejection {
    const cells = computeShipCells(at, size, dir);

    if (cells.some((c) => !inBounds(c))) {
        return 'outOfBounds';
    }

    if (cells.some((c) => field[c.row][c.column].ship != null)) {
        return 'occupied';
    }

    const cellKeys = new Set(cells.map((c) => `${c.row}-${c.column}`));
    const tooClose = cells.some((c) => {
        const fieldCell = field[c.row][c.column];
        if (fieldCell.isAvailable) {
            return false;
        }
        // A cell that is unavailable only because it's one of this same placement's own
        // target cells isn't "too close" — this can't actually happen for an unoccupied
        // cell today (moats only clear on removal), but is kept for correctness/documentation.
        return !cellKeys.has(`${c.row}-${c.column}`) || fieldCell.ship == null;
    });
    if (tooClose) {
        return 'tooClose';
    }

    return null;
}

export function PreparationScreen() {
    const {t} = useTranslation(['screens', 'notifications', 'errors', 'common']);
    const navigate = useNavigate();
    const notify = useNotify();
    const {push} = useToastContext();
    const adapter = useGameAdapter();

    const {sessionId, player} = useSessionGuard();
    const {
        ships,
        field,
        direction,
        setDirection,
        activeShipId,
        setActiveShipId,
        placeShip,
        removeShipAt,
        markReady,
        opponentReady,
        allPlaced,
        loading,
        error,
        actionTick,
    } = usePreparation(sessionId ?? '', player?.playerId ?? '');

    // usePreparation's placeShip/removeShipAt/markReady swallow adapter failures internally
    // and expose them only via the reactive `error` field (they never reject) — so this
    // screen can't tell success from failure with a plain try/catch around those calls.
    // `error` only reaches this component on the *next* render, one tick after the internal
    // setError() call, so it can't be read synchronously right after `await`ing the action.
    // Instead, an effect watching `actionTick` (NOT `error` itself — see usePreparation.ts's
    // doc comment on `actionTick`: two consecutive successful actions both set `error` to
    // the same `null` value, and React skips re-running an effect keyed on `[error]` when a
    // setter is called with an `Object.is`-equal value, so that effect could silently never
    // fire again after the first successful action) resolves whichever promise is currently
    // "waiting" for the next update. Every action ends in exactly one actionTick increment
    // (paired with one setError() call — null on success, an error on failure — see
    // usePreparation.ts), and every action triggered by this screen's UI runs to completion
    // (awaited) before the next one can start, so at most one waiter is ever pending — no
    // request-id bookkeeping needed.
    const waiterRef = useRef<((e: GameAdapterError | null) => void) | null>(null);

    // Set to true immediately before a *placement* runAction call (see handleCellClick's
    // placement branch below); consumed (read once, reset to false) by the actionTick
    // effect below, once ships/field have already refreshed. Reset to false at the top of
    // every OTHER runAction call site so a stray leftover `true` is never misread as "the
    // action that just completed was a placement".
    const pendingAutoAdvanceRef = useRef(false);

    useEffect(() => {
        if (waiterRef.current) {
            waiterRef.current(error);
            waiterRef.current = null;
        }

        if (pendingAutoAdvanceRef.current) {
            pendingAutoAdvanceRef.current = false;
            if (error === null) {
                // "Next" = the next entry in ShipTray's own sort order among still-unplaced
                // ships (descending by size, stable ties preserve `ships`' original order).
                const nextShip = [...ships].sort((a, b) => b.shipSize - a.shipSize)[0];
                setActiveShipId(nextShip ? nextShip.shipId : null);
            }
        }
        // Deliberately keyed on actionTick, not on `error` itself — see comment above.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [actionTick]);

    // Post-ready stage watch: usePreparation doesn't track the player's own stage
    // transition (only opponentReady), so this screen owns polling getStage() itself
    // once the player has marked ready, to detect the server advancing to IN_GAME.
    const [watchingStage, setWatchingStage] = useState(false);

    const watchStage = async () => {
        if (!sessionId) return;
        try {
            const stage = await adapter.getStage(sessionId);
            if (!STILL_PREPARING_STAGES.has(stage) && stage !== '') {
                saveStage(stage);
                if (stage === 'IN_GAME') {
                    navigate('/game/gameplay', {replace: true});
                }
            }
        } catch {
            // Transient poll failures are non-fatal here — usePreparation's own error
            // state already surfaces adapter problems; just retry on the next tick.
        }
    };

    usePolling(watchStage, 3000, watchingStage);

    const placedShips = useMemo(() => derivePlacedShips(field), [field]);

    const fleet: ShipItemData[] = useMemo(() => {
        const unplacedEntries: ShipItemData[] = ships.map((s) => ({
            shipId: s.shipId,
            shipSize: s.shipSize,
            typeName: getShipTypeLabel(SIZE_TO_TYPE[s.shipSize], t),
            placed: false,
            active: s.shipId === activeShipId,
        }));
        const placedEntries: ShipItemData[] = placedShips.map((s) => ({
            shipId: s.shipId,
            shipSize: s.shipSize,
            typeName: getShipTypeLabel(SIZE_TO_TYPE[s.shipSize], t),
            placed: true,
            active: false,
        }));
        return [...unplacedEntries, ...placedEntries];
    }, [ships, placedShips, activeShipId, t]);

    if (!sessionId || !player) {
        // Defensive only — the routing layer's StageGuard is responsible for redirecting
        // away from this screen when session/player data isn't present.
        return null;
    }

    if (loading && field.length === 0 && ships.length === 0) {
        return <LoadingView title={t('screens:loading.title')} subtitle={t('screens:loading.subtitle')}/>;
    }

    function reportRejection(reason: Exclude<PlacementRejection, null>) {
        const key = reason === 'outOfBounds' ? 'place.outOfBounds' : reason === 'occupied' ? 'place.occupied' : 'place.tooClose';
        notify.error(key);
    }

    function reportBackendFailure(e: GameAdapterError) {
        push({
            variant: 'err',
            title: t('notifications:error.generic.title'),
            message: t(`errors:${resolveErrorMessageKey(e)}`),
        });
    }

    /**
     * Runs a usePreparation action (placeShip/removeShipAt/markReady) and reports whether
     * it succeeded, bridging the one-tick gap described above via `waiterRef`.
     */
    async function runAction(action: () => Promise<void>): Promise<boolean> {
        const resultPromise = new Promise<GameAdapterError | null>((resolve) => {
            waiterRef.current = resolve;
        });

        await action();
        const resultError = await resultPromise;

        if (resultError) {
            reportBackendFailure(resultError);
            return false;
        }
        return true;
    }

    async function handleSelectShip(shipId: string) {
        setActiveShipId(shipId);
    }

    async function handleRemoveShip(shipId: string) {
        const cell = findCellForShip(field, shipId);
        if (!cell) {
            return;
        }
        pendingAutoAdvanceRef.current = false;
        const ok = await runAction(() => removeShipAt({row: cell.row, column: cell.col}));
        if (ok) {
            notify.success('ship.removed');
            if (activeShipId === shipId) {
                setActiveShipId(null);
            }
        }
    }

    async function handleCellClick(row: number, col: number) {
        const cell = field[row][col];

        // Two-way removal takes precedence: tapping an already-placed ship on the board
        // removes it, regardless of whether a ship is currently selected for placement.
        if (cell.ship != null) {
            const shipId = cell.ship.shipId;
            pendingAutoAdvanceRef.current = false;
            const ok = await runAction(() => removeShipAt({row, column: col}));
            if (ok) {
                notify.success('ship.removed');
                if (activeShipId === shipId) {
                    setActiveShipId(null);
                }
            }
            return;
        }

        if (!activeShipId) {
            return;
        }

        const activeShip = ships.find((s) => s.shipId === activeShipId);
        if (!activeShip) {
            return;
        }

        const rejection = validatePlacement(field, {row, column: col}, activeShip.shipSize, direction);
        if (rejection) {
            reportRejection(rejection);
            return;
        }

        pendingAutoAdvanceRef.current = true;
        const ok = await runAction(() => placeShip(activeShipId, {row, column: col}));
        if (ok) {
            notify.success('ship.placed');
        }
    }

    async function handleReady() {
        if (!allPlaced) {
            notify.warn('ready.needAllShips');
            return;
        }
        pendingAutoAdvanceRef.current = false;
        const ok = await runAction(() => markReady());
        if (ok) {
            setWatchingStage(true);
        }
    }

    const totalShips = ships.length + placedShips.length;

    return (
        <div className="screen">
            <div className="prep-screen-head">
                <div>
                    <h2 className="title">{t('screens:preparation.title')}</h2>
                    <p className="sub">{t('screens:preparation.progress', {
                        placed: placedShips.length,
                        total: totalShips
                    })}</p>
                </div>
                <Pill variant={opponentReady ? 'ok' : 'warn'}>
                    {t('screens:preparation.opponentLabel')}:{' '}
                    {opponentReady ? t('screens:preparation.opponentStatusReady') : t('screens:preparation.opponentStatusInProgress')}
                </Pill>
            </div>

            <div className="prep-grid">
                <div className="fleet-panel-wrap">
                    <div className="prep-dir-toggle-wrap">
                        <DirectionToggle
                            direction={direction}
                            onChange={setDirection}
                            horizontalLabel={t('screens:preparation.directionHorizontal')}
                            verticalLabel={t('screens:preparation.directionVertical')}
                        />
                    </div>
                    <ShipTray
                        ships={fleet}
                        activeShipId={activeShipId}
                        onSelectShip={handleSelectShip}
                        onRemoveShip={handleRemoveShip}
                        fleetLabel={t('screens:preparation.fleetLabel')}
                        hint={t('screens:preparation.hint')}
                        cellSingularLabel={t('screens:preparation.cellSingular')}
                        cellPluralLabel={t('screens:preparation.cellPlural')}
                        removeLabel={(typeName) => `${t('screens:preparation.placedLabel')}: ${typeName} ✕`}
                    />
                </div>

                <div className="board-panel">
                    <div className="board-head">
                        <h3>{t('screens:preparation.boardLabel')}</h3>
                        <span className="tag">{t('screens:preparation.boardTag')}</span>
                    </div>
                    {/* ghostCells (valid-drop hover preview) intentionally omitted — this is a
              click-only board (no drag/hover-driven placement state), so there is no
              natural "hovering a starting cell" moment to compute a live preview from
              without adding pointer-move tracking across 100 cells purely for a nice-to-have.
              Documented simplification per the ticket's acceptable fallback: the core
              click-to-place/click-to-remove mechanic works fully without it. */}
                    <Board field={field} mode="prep" onCellClick={handleCellClick}/>
                    <Button variant="ok" className="prep-ready-btn" disabled={!allPlaced} onClick={handleReady}>
                        ✓ {t('screens:preparation.readyButton')}
                    </Button>
                    <Legend
                        withNoGo={true}
                        labels={{
                            water: t('screens:legend.water'),
                            ship: t('screens:legend.ship'),
                            hit: t('screens:legend.hit'),
                            miss: t('screens:legend.miss'),
                            sunk: t('screens:legend.sunk'),
                            noGo: t('screens:legend.block'),
                        }}
                    />
                </div>
            </div>
        </div>
    );
}
