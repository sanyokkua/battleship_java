import {useEffect, useMemo, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate} from 'react-router-dom';
import {useSessionGuard} from '../hooks/useSessionGuard';
import {usePreparation} from '../hooks/usePreparation';
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
import {type ShipPlacementOption, ShipPlacementPopup} from '../widgets/preparation/ShipPlacementPopup';
import {ShipActionPopup} from '../widgets/preparation/ShipActionPopup';
import {
    computeGroupedEligibleShips,
    computeValidRotation,
    derivePlacedShipPlacement,
    type PlacementRejection,
    validatePlacement,
} from './preparationPlacement';
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
 * Ship-placement screen ("/game/preparation") — shown during the `PREPARATION`
 * stage. Delegates fleet/board/placement state to `usePreparation` (which
 * wraps `GameAdapter` calls for placing/removing ships and marking ready, and
 * also exposes the session's pushed `stage`) and, once the player has readied
 * up, watches that pushed `stage` to detect the server advancing to `IN_GAME`
 * and navigate to `GameplayScreen` ("/game/gameplay"). Click-to-place/click-to-remove
 * placements are pre-validated client-side via {@link validatePlacement}
 * before the adapter call is made.
 */
export function PreparationScreen() {
    const {t} = useTranslation(['screens', 'notifications', 'errors', 'common']);
    const navigate = useNavigate();
    const notify = useNotify();
    const {push} = useToastContext();

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
        stage,
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

    // Tap-empty-cell guided placement popup: set when the player taps an empty cell with
    // no ship selected in the tray (the tray-first flow above is unaffected by this).
    const [placementCell, setPlacementCell] = useState<Coordinate | null>(null);
    const [placementPending, setPlacementPending] = useState(false);

    // Tap-placed-ship rotate/remove popup: set whenever the player taps any cell of an
    // already-placed ship, replacing the previous instant-remove-on-tap behavior.
    const [actionPopup, setActionPopup] = useState<{ shipId: string; shipSize: number } | null>(null);
    const [actionPending, setActionPending] = useState(false);

    useEffect(() => {
        if (!watchingStage || !stage) {
            return;
        }
        if (!STILL_PREPARING_STAGES.has(stage) && stage !== '') {
            saveStage(stage);
            if (stage === 'IN_GAME') {
                navigate('/game/gameplay', {replace: true});
            }
        }
    }, [watchingStage, stage, navigate]);

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

    const placementOptions: ShipPlacementOption[] = useMemo(() => {
        if (!placementCell) {
            return [];
        }
        return computeGroupedEligibleShips(field, placementCell, ships).map((group) => ({
            shipId: group.representativeShipId,
            shipSize: group.shipSize,
            typeName: getShipTypeLabel(SIZE_TO_TYPE[group.shipSize], t),
            count: group.count,
            directions: group.directions,
        }));
    }, [field, placementCell, ships, t]);

    const actionPopupDetails = useMemo(() => {
        if (!actionPopup) {
            return null;
        }
        const placement = derivePlacedShipPlacement(field, actionPopup.shipId);
        if (!placement) {
            return null;
        }
        const canRotate = actionPopup.shipSize > 1
            && computeValidRotation(field, actionPopup.shipId, placement.at, actionPopup.shipSize, placement.direction) != null;
        return {
            typeName: getShipTypeLabel(SIZE_TO_TYPE[actionPopup.shipSize], t),
            placement,
            canRotate,
        };
    }, [field, actionPopup, t]);

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
     * it succeeded, bridging the one-tick gap described above via `waiterRef`. Pass
     * `suppressErrorToast` when the caller wants to show its own, more specific message
     * instead of the generic one (e.g. rotate's re-add failure — see `handleRotate`).
     */
    async function runAction(action: () => Promise<void>, options?: {
        suppressErrorToast?: boolean
    }): Promise<boolean> {
        const resultPromise = new Promise<GameAdapterError | null>((resolve) => {
            waiterRef.current = resolve;
        });

        await action();
        const resultError = await resultPromise;

        if (resultError) {
            if (!options?.suppressErrorToast) {
                reportBackendFailure(resultError);
            }
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

        // Tapping any cell of an already-placed ship opens the rotate/remove popup, taking
        // precedence regardless of whether a ship is currently selected for placement.
        if (cell.ship != null) {
            setActionPopup({shipId: cell.ship.shipId, shipSize: cell.ship.shipSize});
            return;
        }

        if (!activeShipId) {
            // No tray selection: open the guided placement popup instead of a no-op, as
            // long as there's at least one unplaced ship to offer (matches the tray flow's
            // own behavior of having nothing to do once every ship is placed).
            if (ships.length > 0) {
                setPlacementCell({row, column: col});
            }
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

    async function handlePlacementConfirm(shipId: string, dir: ShipDirection) {
        if (!placementCell) {
            return;
        }
        // Keep the top-level toggle in sync with the popup's choice for any subsequent
        // tray-first placements, but pass `dir` explicitly to `placeShip` too — `setDirection`
        // only takes effect on the next render, so relying on it alone here would place using
        // whatever direction the toggle showed *before* this call.
        setDirection(dir);
        pendingAutoAdvanceRef.current = false;
        setPlacementPending(true);
        const ok = await runAction(() => placeShip(shipId, placementCell, dir));
        setPlacementPending(false);
        setPlacementCell(null);
        if (ok) {
            notify.success('ship.placed');
        }
    }

    async function handleRemoveFromPopup() {
        if (!actionPopup || !actionPopupDetails) {
            return;
        }
        const {shipId} = actionPopup;
        const {at} = actionPopupDetails.placement;
        pendingAutoAdvanceRef.current = false;
        setActionPending(true);
        const ok = await runAction(() => removeShipAt(at));
        setActionPending(false);
        setActionPopup(null);
        if (ok) {
            notify.success('ship.removed');
            if (activeShipId === shipId) {
                setActiveShipId(null);
            }
        }
    }

    async function handleRotateFromPopup() {
        if (!actionPopup || !actionPopupDetails || !actionPopupDetails.canRotate) {
            return;
        }
        const {shipId, shipSize} = actionPopup;
        const {at, direction: currentDirection} = actionPopupDetails.placement;
        const rotatedDirection = computeValidRotation(field, shipId, at, shipSize, currentDirection);
        if (!rotatedDirection) {
            return;
        }

        pendingAutoAdvanceRef.current = false;
        setActionPending(true);

        const removed = await runAction(() => removeShipAt(at));
        if (!removed) {
            // Nothing changed server-side — the generic failure toast from runAction already
            // explains it; nothing further to do.
            setActionPending(false);
            setActionPopup(null);
            return;
        }

        const added = await runAction(() => placeShip(shipId, at, rotatedDirection), {suppressErrorToast: true});
        setActionPending(false);
        setActionPopup(null);
        if (added) {
            notify.success('ship.rotated');
        } else {
            // Remove succeeded but the re-add didn't — the ship is now actually gone
            // server-side (usePreparation's refetch already reflects this). A generic
            // "something went wrong" toast would wrongly suggest nothing happened.
            notify.error('ship.rotateFailed');
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

            <ShipPlacementPopup
                open={placementCell != null}
                options={placementOptions}
                disabled={placementPending}
                onClose={() => setPlacementCell(null)}
                onConfirm={handlePlacementConfirm}
                pickShipTitle={t('screens:preparation.popup.pickShipTitle')}
                pickDirectionTitle={t('screens:preparation.popup.pickDirectionTitle')}
                emptyStateMessage={t('screens:preparation.popup.emptyState')}
                closeLabel={t('screens:preparation.popup.close')}
                backLabel={t('screens:preparation.popup.back')}
                horizontalLabel={t('screens:preparation.directionHorizontal')}
                verticalLabel={t('screens:preparation.directionVertical')}
                cellSingularLabel={t('screens:preparation.cellSingular')}
                cellPluralLabel={t('screens:preparation.cellPlural')}
                availableLabel={t('screens:preparation.popup.available')}
            />

            <ShipActionPopup
                open={actionPopup != null}
                shipTypeName={actionPopupDetails?.typeName ?? ''}
                shipSize={actionPopup?.shipSize ?? 0}
                canRotate={actionPopupDetails?.canRotate ?? false}
                disabled={actionPending}
                onClose={() => setActionPopup(null)}
                onRotate={handleRotateFromPopup}
                onRemove={handleRemoveFromPopup}
                rotateLabel={t('screens:preparation.popup.rotate')}
                removeLabel={t('screens:preparation.popup.remove')}
                cellSingularLabel={t('screens:preparation.cellSingular')}
                cellPluralLabel={t('screens:preparation.cellPlural')}
            />
        </div>
    );
}
