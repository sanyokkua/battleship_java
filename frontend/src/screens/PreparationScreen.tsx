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
import {type ShipPlacementOption, ShipPlacementPopup} from '../widgets/preparation/ShipPlacementPopup';
import {ShipActionPopup} from '../widgets/preparation/ShipActionPopup';
import {computeGroupedEligibleShips, computeValidRotation, derivePlacedShipPlacement,} from './preparationPlacement';
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

/**
 * Ship-placement screen ("/game/preparation") — shown during the `PREPARATION`
 * stage. Delegates fleet/board/placement state to `usePreparation` (which
 * wraps `GameAdapter` calls for placing/removing ships and marking ready, and
 * also exposes the session's pushed `stage`) and, once the player has readied
 * up, watches that pushed `stage` to detect the server advancing to `IN_GAME`
 * and navigate to `GameplayScreen` ("/game/gameplay"). Tapping an empty cell
 * opens {@link ShipPlacementPopup}, which pre-filters ship/direction options to
 * only those valid at that cell, so an invalid placement combination can never
 * be chosen through the UI.
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
        placeShip,
        removeShipAt,
        markReady,
        opponentReady,
        stage,
        allPlaced,
        loading,
        error,
        actionTick,
        refresh,
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

    useEffect(() => {
        if (waiterRef.current) {
            waiterRef.current(error);
            waiterRef.current = null;
        }
        // Deliberately keyed on actionTick, not on `error` itself — see comment above.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [actionTick]);

    // Post-ready stage watch: usePreparation doesn't track the player's own stage
    // transition (only opponentReady), so this screen owns polling getStage() itself
    // once the player has marked ready, to detect the server advancing to IN_GAME.
    const [watchingStage, setWatchingStage] = useState(false);

    // Tap-empty-cell guided placement popup: set whenever the player taps an empty cell
    // with ships left to place.
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

    async function handleCellClick(row: number, col: number) {
        const cell = field[row][col];

        // Tapping any cell of an already-placed ship opens the rotate/remove popup.
        if (cell.ship != null) {
            setActionPopup({shipId: cell.ship.shipId, shipSize: cell.ship.shipSize});
            return;
        }

        if (ships.length > 0) {
            setPlacementCell({row, column: col});
        }
    }

    async function handlePlacementConfirm(shipId: string, dir: ShipDirection) {
        if (!placementCell) {
            return;
        }
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
        const {at} = actionPopupDetails.placement;
        setActionPending(true);
        const ok = await runAction(() => removeShipAt(at));
        setActionPending(false);
        setActionPopup(null);
        if (ok) {
            notify.success('ship.removed');
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

            <Button variant="ghost" size="sm" onClick={() => void refresh()}>
                ⟳ {t('common:button.refresh')}
            </Button>

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
