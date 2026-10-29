import { useState, type Ref } from 'react';
import type { components } from '../contract/generated/api';
import { Board } from '../components/Board';
import { FleetTray } from '../components/FleetTray';
import { Button } from '../components/Button';
import { Pill } from '../components/Pill';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { Stepper } from '../components/Stepper';
import { GatewayError, sendCommand, type Ruleset } from '../gateway/gameGateway';
import { useGame } from '../session/useGame';
import type { GameId } from '../session/gameStore';
import { useT } from '../i18n/useT';
import { ShipActionDialog } from './ShipActionDialog';
import './Placement.css';
import { usePlacementDrag } from './usePlacementDrag';

type Ship = components['schemas']['Ship'];
type Coordinate = components['schemas']['Coordinate'];
export function Placement({
    gameId,
    leaveSlotRef,
    ruleset,
    streamReplaced,
}: {
    gameId: GameId;
    leaveSlotRef: Ref<HTMLDivElement>;
    ruleset: Ruleset;
    streamReplaced: boolean;
}) {
    const t = useT();
    const state = useGame(gameId);
    const snapshot = state.snapshot;
    const [target, setTarget] = useState<{ anchor: Coordinate; ship?: Ship | undefined; editing: boolean }>();
    const [moving, setMoving] = useState<Ship>();
    const [shipDialogOpen, setShipDialogOpen] = useState(false);
    const [confirmReady, setConfirmReady] = useState(false);
    const [pending, setPending] = useState(false);
    const [error, setError] = useState<string>();
    const [orientation, setOrientation] = useState<components['schemas']['Orientation']>('HORIZONTAL');
    const allowed = snapshot?.allowedActions ?? [];
    const disabled = pending || state.connection !== 'connected' || streamReplaced;
    const command = async (action: components['schemas']['Command']) => {
        if (disabled) return;
        if (action.type === 'PLACE_SHIP') setOrientation(action.orientation);
        setPending(true);
        setError(undefined);
        // Close while the opener still exists, so Dialog can restore focus.
        setShipDialogOpen(false);
        setMoving(undefined);
        setConfirmReady(false);
        try {
            await sendCommand(gameId, { commandId: crypto.randomUUID(), command: action });
        } catch (failure) {
            setError(failure instanceof GatewayError ? failure.problem.messageKey : 'problem.serviceUnavailable');
        } finally {
            setPending(false);
        }
    };
    const { boardRef, preview, draggingPlacedShip, start, handlers } = usePlacementDrag({
        dimensions: ruleset.board,
        disabled: disabled || !allowed.includes('PLACE_SHIP'),
        onPlace: (ship, direction, anchor) => {
            void command({ type: 'PLACE_SHIP', shipId: ship.shipId, orientation: direction, anchor });
        },
    });
    if (!snapshot) return null;
    const activate = (anchor: Coordinate) => {
        const placedShip = snapshot.yourBoard.ships.find((ship) =>
            ship.cells.some((cell) => cell.rowIndex === anchor.rowIndex && cell.columnIndex === anchor.columnIndex),
        );
        const ship = moving ?? placedShip;
        const editing = moving === undefined && placedShip !== undefined;
        setShipDialogOpen(true);
        setTarget({ anchor: editing && ship?.anchor ? ship.anchor : anchor, ship, editing });
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
                disabled={disabled || !allowed.includes('PLACE_SHIP')}
                onShipPointerDown={(event, ship) => {
                    start(event, ship, orientation);
                }}
                onSelect={(ship) => {
                    setShipDialogOpen(true);
                    setTarget({ anchor: { rowIndex: 0, columnIndex: 0 }, ship, editing: false });
                }}
            />
            <div className="placement-stage">
                <div className="board-panel" ref={boardRef}>
                    <h2>{t('placement.yourSea')}</h2>
                    <Board
                        board={snapshot.yourBoard}
                        dimensions={ruleset.board}
                        label={t('placement.yourSea')}
                        placementBlockedCells={
                            moving || draggingPlacedShip ? undefined : snapshot.placementBlockedCells
                        }
                        disabled={disabled || (!allowed.includes('PLACE_SHIP') && !allowed.includes('REMOVE_SHIP'))}
                        onActivate={activate}
                        onBlockedActivate={() => {
                            setError('problem.placementTouching');
                        }}
                        dragTarget={preview}
                        onShipPointerDown={
                            allowed.includes('PLACE_SHIP')
                                ? (event, coordinate) => {
                                      const ship = snapshot.yourBoard.ships.find((entry) =>
                                          entry.cells.some(
                                              (cell) =>
                                                  cell.rowIndex === coordinate.rowIndex &&
                                                  cell.columnIndex === coordinate.columnIndex,
                                          ),
                                      );
                                      if (ship) start(event, ship, ship.orientation ?? 'HORIZONTAL', coordinate);
                                  }
                                : undefined
                        }
                    />
                </div>
                <div className="placement-status">
                    <p>{t(ruleset.shipsMayTouch ? 'placement.touchAllowed' : 'placement.noTouch')}</p>
                    {error && <p role="alert">{t(error)}</p>}
                    <p role="status">
                        {t(
                            snapshot.you.ready
                                ? 'placement.waiting'
                                : allowed.includes('READY')
                                  ? 'placement.fleetComplete'
                                  : 'placement.incomplete',
                        )}
                    </p>
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
                                disabled={disabled}
                                onClick={() => {
                                    setConfirmReady(true);
                                }}
                            >
                                {t('placement.ready')}
                            </Button>
                        )}
                        {moving && (
                            <Button
                                onClick={() => {
                                    setMoving(undefined);
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
                    onClose={() => {
                        setShipDialogOpen(false);
                    }}
                    onOrientationChange={setOrientation}
                    onPickAnchor={(ship) => {
                        setOrientation(ship.orientation ?? 'HORIZONTAL');
                        setMoving(ship);
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
