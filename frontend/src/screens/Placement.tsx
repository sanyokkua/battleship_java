import { useCallback, useState, type Ref } from 'react';
import type { components } from '../contract/generated/api';
import { Board, BoardLegend } from '../components/Board';
import { FleetTray } from '../components/FleetTray';
import { Button } from '../components/Button';
import { Pill } from '../components/Pill';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { Stepper } from '../components/Stepper';
import type { Ruleset } from '../gateway/gameGateway';
import { useGame } from '../session/useGame';
import { getGameStore, type GameId } from '../session/gameStore';
import { useRetryWindow } from '../session/useRetryWindow';
import type { MessageKey } from '../i18n/translate';
import { useT } from '../i18n/useT';
import { ShipActionDialog } from './ShipActionDialog';
import './Placement.css';
import { usePlacementDrag } from './usePlacementDrag';
import { canPlace, movingBlocked } from './placementRules';

type Ship = components['schemas']['Ship'];
type Coordinate = components['schemas']['Coordinate'];
type Action = components['schemas']['Action'];
type Snapshot = components['schemas']['GameSnapshot'];

function placementReadiness(ready: boolean, allowed: Action[]): MessageKey {
    if (ready) return 'placement.waiting';
    return allowed.includes('READY') ? 'placement.fleetComplete' : 'placement.incomplete';
}

function shipAt(ships: Ship[], anchor: Coordinate): Ship | undefined {
    return ships.find((ship) =>
        ship.cells.some((cell) => cell.rowIndex === anchor.rowIndex && cell.columnIndex === anchor.columnIndex),
    );
}

function blockedFor(snapshot: Snapshot, liftedShip: Ship | undefined, ruleset: Ruleset): Coordinate[] | undefined {
    if (!liftedShip) return snapshot.placementBlockedCells;
    return movingBlocked(snapshot.yourBoard.ships, liftedShip, ruleset.board, ruleset.shipsMayTouch);
}

export function Placement({
    gameId,
    leaveSlotRef,
    ruleset,
    streamReplaced,
    onCommand,
}: Readonly<{
    gameId: GameId;
    leaveSlotRef: Ref<HTMLDivElement>;
    ruleset: Ruleset;
    streamReplaced: boolean;
    onCommand: (request: components['schemas']['CommandRequest']) => Promise<void>;
}>) {
    const t = useT();
    const state = useGame(gameId);
    const snapshot = state.snapshot;
    const [targetState, setTargetState] = useState<{
        anchor: Coordinate;
        ship?: Ship | undefined;
        editing: boolean;
        boardKey: string;
    }>();
    const [movingState, setMovingState] = useState<{ ship: Ship; boardKey: string }>();
    const [selectedState, setSelectedState] = useState<{ ship: Ship; boardKey: string }>();
    const [shipDialogOpen, setShipDialogOpen] = useState(false);
    const closeShipDialog = useCallback(() => {
        setShipDialogOpen(false);
    }, []);
    const [confirmReady, setConfirmReady] = useState(false);
    const [error, setError] = useState<MessageKey>();
    const [orientation, setOrientation] = useState<components['schemas']['Orientation']>('HORIZONTAL');
    const allowed = snapshot?.allowedActions ?? [];
    const boardKey = JSON.stringify(snapshot?.yourBoard.ships);
    const editingAllowed = allowed.includes('PLACE_SHIP') || allowed.includes('REMOVE_SHIP');
    const target = editingAllowed && targetState?.boardKey === boardKey ? targetState : undefined;
    const moving = allowed.includes('PLACE_SHIP') && movingState?.boardKey === boardKey ? movingState.ship : undefined;
    const selected =
        allowed.includes('PLACE_SHIP') && selectedState?.boardKey === boardKey ? selectedState.ship : undefined;
    const retryBlocked = useRetryWindow(state.notice?.retryAtMs);
    const disabled = state.action !== undefined || state.connection !== 'connected' || streamReplaced || retryBlocked;
    const command = async (action: components['schemas']['Command']) => {
        if (disabled) return;
        const request = getGameStore(gameId).beginAction(action);
        if (!request) return;
        if (action.type === 'PLACE_SHIP') setOrientation(action.orientation);
        setError(undefined);
        // Restore focus to the selected board cell before closing the popup.
        boardRef.current
            ?.querySelector<HTMLElement>(
                `[data-coordinate="${String(target?.anchor.rowIndex)}:${String(target?.anchor.columnIndex)}"]`,
            )
            ?.focus();
        setShipDialogOpen(false);
        setMovingState(undefined);
        setSelectedState(undefined);
        setConfirmReady(false);
        await onCommand(request);
    };
    const { boardRef, preview, draggingPlacedShip, start, handlers } = usePlacementDrag({
        dimensions: ruleset.board,
        disabled: disabled || !allowed.includes('PLACE_SHIP'),
        onPlace: (ship, direction, anchor) => {
            if (!snapshot) return;
            if (
                ship.length === 1 &&
                canPlace(ship, anchor, 'HORIZONTAL', snapshot.yourBoard.ships, ruleset.board, ruleset.shipsMayTouch)
            ) {
                void command({ type: 'PLACE_SHIP', shipId: ship.shipId, orientation: 'HORIZONTAL', anchor });
            } else {
                setMovingState(ship.cells.length ? { ship, boardKey } : undefined);
                setSelectedState(!ship.cells.length ? { ship, boardKey } : undefined);
                setTargetState({
                    anchor,
                    ship: { ...ship, orientation: direction },
                    editing: false,
                    boardKey,
                });
                setShipDialogOpen(true);
            }
        },
    });
    if (!snapshot) return null;
    const liftedShip = moving ?? draggingPlacedShip;
    const activate = (anchor: Coordinate) => {
        const placedShip = shipAt(snapshot.yourBoard.ships, anchor);
        const ship = moving ?? selected ?? placedShip;
        const editing = moving === undefined && selected === undefined && placedShip !== undefined;
        if (!editing && !allowed.includes('PLACE_SHIP')) return;
        if (
            !editing &&
            ship?.length === 1 &&
            canPlace(ship, anchor, 'HORIZONTAL', snapshot.yourBoard.ships, ruleset.board, ruleset.shipsMayTouch)
        ) {
            void command({ type: 'PLACE_SHIP', shipId: ship.shipId, orientation: 'HORIZONTAL', anchor });
            return;
        }
        setShipDialogOpen(true);
        setTargetState({ anchor, ship, editing, boardKey });
    };
    return (
        <section className="placement-screen" {...handlers}>
            <div className="placement-heading">
                <div>
                    <p className="eyebrow">{t('placement.label')}</p>
                    <h1>{t('game.placement')}</h1>
                    <p>{t(moving ? 'placement.pickAnchor' : 'placement.help')}</p>
                </div>
                <Pill tone={snapshot.opponent?.ready ? 'good' : 'neutral'}>
                    {t(snapshot.opponent?.ready ? 'placement.opponentReady' : 'placement.opponentPlacing')}
                </Pill>
                <div ref={leaveSlotRef} />
            </div>
            <ConnectionStatus connection={state.connection} replaced={streamReplaced} />
            <Stepper current="Placement" />
            <FleetTray
                ships={snapshot.yourBoard.ships}
                disabled={disabled || !allowed.includes('PLACE_SHIP') || !!moving}
                selectedShipId={selected?.shipId}
                onShipPointerDown={(event, ship) => {
                    start(event, ship, orientation);
                }}
                onSelect={(ship) => {
                    setSelectedState({ ship, boardKey });
                    setShipDialogOpen(false);
                }}
            />
            <div className="placement-stage">
                <div className="board-panel" ref={boardRef}>
                    <h2>{t('placement.yourSea')}</h2>
                    <Board
                        board={snapshot.yourBoard}
                        dimensions={ruleset.board}
                        label={t('placement.yourSea')}
                        placementBlockedCells={blockedFor(snapshot, liftedShip, ruleset)}
                        liftedCells={(moving ?? draggingPlacedShip)?.cells}
                        disabled={disabled || (!allowed.includes('PLACE_SHIP') && !allowed.includes('REMOVE_SHIP'))}
                        onActivate={activate}
                        onBlockedActivate={() => {
                            setError('problem.placementTouching');
                        }}
                        dragTarget={preview}
                        onShipPointerDown={
                            allowed.includes('PLACE_SHIP')
                                ? (event, coordinate) => {
                                      const ship = shipAt(snapshot.yourBoard.ships, coordinate);
                                      if (ship) start(event, ship, ship.orientation ?? 'HORIZONTAL', coordinate);
                                  }
                                : undefined
                        }
                    />
                    <BoardLegend placement />
                </div>
                <div className="placement-status">
                    <p>{t(ruleset.shipsMayTouch ? 'placement.touchAllowed' : 'placement.noTouch')}</p>
                    {error && <p role="alert">{t(error)}</p>}
                    <output>{t(placementReadiness(snapshot.you.ready, allowed))}</output>
                    <div className="placement-actions">
                        {allowed.includes('PLACE_FLEET_RANDOMLY') && (
                            <Button
                                disabled={disabled}
                                onClick={() => {
                                    void command({ type: 'PLACE_FLEET_RANDOMLY' });
                                }}
                            >
                                {t('placement.random')}
                            </Button>
                        )}
                        {allowed.includes('CLEAR_FLEET') && (
                            <Button
                                disabled={disabled}
                                onClick={() => {
                                    void command({ type: 'CLEAR_FLEET' });
                                }}
                            >
                                {t('placement.clear')}
                            </Button>
                        )}
                        {allowed.includes('READY') && (
                            <Button
                                variant="primary"
                                disabled={disabled || !!moving}
                                onClick={() => {
                                    setConfirmReady(true);
                                }}
                            >
                                {t('placement.ready')}
                            </Button>
                        )}
                        {(moving ?? selected) && (
                            <Button
                                onClick={() => {
                                    const original = moving?.anchor;
                                    const cell = original
                                        ? boardRef.current?.querySelector<HTMLElement>(
                                              `[data-coordinate="${String(original.rowIndex)}:${String(original.columnIndex)}"]`,
                                          )
                                        : boardRef.current?.querySelector<HTMLElement>(
                                              '[role="gridcell"][tabindex="0"]',
                                          );
                                    cell?.focus();
                                    setMovingState(undefined);
                                    setSelectedState(undefined);
                                    setShipDialogOpen(false);
                                }}
                            >
                                {t('common.cancel')}
                            </Button>
                        )}
                    </div>
                </div>
            </div>
            {target && (
                <ShipActionDialog
                    key={`${String(target.anchor.rowIndex)},${String(target.anchor.columnIndex)},${target.ship?.shipId ?? ''},${target.ship?.orientation ?? ''},${String(target.editing)}`}
                    open={shipDialogOpen}
                    anchor={target.anchor}
                    ships={snapshot.yourBoard.ships}
                    selectedShip={target.ship}
                    editing={target.editing}
                    allowed={allowed}
                    onClose={closeShipDialog}
                    dimensions={ruleset.board}
                    shipsMayTouch={ruleset.shipsMayTouch}
                    anchorElement={boardRef.current?.querySelector<HTMLElement>(
                        `[data-coordinate="${String(target.anchor.rowIndex)}:${String(target.anchor.columnIndex)}"]`,
                    )}
                    onPickAnchor={(ship) => {
                        setOrientation(ship.orientation ?? 'HORIZONTAL');
                        setMovingState({ ship, boardKey });
                        setSelectedState(undefined);
                        setShipDialogOpen(false);
                    }}
                    onCommand={(action) => {
                        void command(action);
                    }}
                />
            )}
            <ConfirmDialog
                open={confirmReady}
                title={t('placement.readyTitle')}
                message={t('placement.readyWarning')}
                confirmLabel={t('placement.commit')}
                onClose={() => {
                    setConfirmReady(false);
                }}
                onConfirm={() => {
                    void command({ type: 'READY' });
                }}
            />
        </section>
    );
}
