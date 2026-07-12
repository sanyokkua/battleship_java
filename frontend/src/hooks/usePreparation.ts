import {useCallback, useEffect, useState} from "react";
import {useGameAdapter} from "../adapters/GameAdapterContext";
import {GameAdapterError, isGameAdapterError} from "../adapters/AdapterErrors";
import {usePolling} from "./usePolling";
import type {CellDto, Coordinate, ShipDirection, ShipDto} from "../logic/ApplicationTypes";

export type PreparationHookState = {
    ships: ShipDto[];
    field: CellDto[][];
    direction: ShipDirection;
    setDirection: (d: ShipDirection) => void;
    activeShipId: string | null;
    setActiveShipId: (id: string | null) => void;
    placeShip: (shipId: string, at: Coordinate) => Promise<void>;
    removeShipAt: (at: Coordinate) => Promise<void>;
    markReady: () => Promise<void>;
    opponentReady: boolean;
    allPlaced: boolean; // every ship in `ships` appears somewhere in `field`
    loading: boolean;
    error: GameAdapterError | null;
    // Increments on every placeShip/removeShipAt/markReady completion (success or
    // failure), independent of whether `error`'s actual value changed. Consumers
    // that need to react to "an action just settled" (e.g. PreparationScreen's
    // one-tick success/failure handshake) should watch this instead of `error`
    // directly: two consecutive successful actions both set `error` to the same
    // `null` value, and React bails out of re-running effects keyed on `[error]`
    // when a state setter is called with a value that's `Object.is`-equal to the
    // current one - so an effect keyed on `[error]` alone can silently miss a
    // successful action that happens to follow another successful action.
    actionTick: number;
};

function toAdapterError(e: unknown, context: string): GameAdapterError {
    return isGameAdapterError(e) ? e : new GameAdapterError("Preparation request failed", {cause: e, context});
}

function computeAllPlaced(ships: ShipDto[], field: CellDto[][]): boolean {
    return ships.every(ship =>
        field.some(row => row.some(cell => cell.ship?.shipId === ship.shipId))
    );
}

export function usePreparation(sessionId: string, playerId: string): PreparationHookState {
    const adapter = useGameAdapter();
    const [ships, setShips] = useState<ShipDto[]>([]);
    const [field, setField] = useState<CellDto[][]>([]);
    const [direction, setDirection] = useState<ShipDirection>("HORIZONTAL");
    const [activeShipId, setActiveShipId] = useState<string | null>(null);
    const [opponentReady, setOpponentReady] = useState<boolean>(false);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<GameAdapterError | null>(null);
    const [actionTick, setActionTick] = useState<number>(0);

    const refetch = useCallback(async () => {
        const state = await adapter.getPreparationState(sessionId, playerId);
        setShips(state.ships);
        setField(state.field);
    }, [adapter, sessionId, playerId]);

    useEffect(() => {
        let cancelled = false;
        // Note: `loading` already starts true (see useState above), so it does not need to be
        // set again here on the initial mount. If sessionId/playerId ever change on an already-
        // mounted instance, this intentionally does NOT flip loading back to true — callers of
        // this hook are expected to key/remount by session+player rather than swap them in place.
        //
        // The setState calls reached from `refetch` (setShips/setField) and from the handlers
        // below (setError/setLoading) only ever run after the awaited adapter call resolves
        // (never synchronously during this render), guarded by `cancelled` so a stale response
        // from an unmounted/re-keyed instance can't clobber later state — this is the standard
        // fetch-on-mount pattern. The linter's static analysis traces into `refetch` and flags
        // this call site as if it set state synchronously, which it does not.
        // eslint-disable-next-line react-hooks/set-state-in-effect -- see comment above
        refetch()
            .catch(e => {
                if (!cancelled) {
                    setError(toAdapterError(e, "usePreparation:init"));
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoading(false);
                }
            });
        return () => {
            cancelled = true;
        };
    }, [refetch]);

    const pollOpponent = useCallback(async () => {
        try {
            const opponent = await adapter.getOpponent(sessionId, playerId);
            setOpponentReady(opponent.ready);
        } catch (e) {
            setError(toAdapterError(e, "usePreparation:pollOpponent"));
        }
    }, [adapter, sessionId, playerId]);

    usePolling(pollOpponent, 3000, true);

    const placeShip = useCallback(async (shipId: string, at: Coordinate) => {
        try {
            await adapter.addShip(sessionId, playerId, shipId, at, direction);
            await refetch();
            setError(null);
        } catch (e) {
            setError(toAdapterError(e, "usePreparation:placeShip"));
        } finally {
            setActionTick(t => t + 1);
        }
    }, [adapter, sessionId, playerId, direction, refetch]);

    const removeShipAt = useCallback(async (at: Coordinate) => {
        try {
            await adapter.removeShip(sessionId, playerId, at);
            await refetch();
            setError(null);
        } catch (e) {
            setError(toAdapterError(e, "usePreparation:removeShipAt"));
        } finally {
            setActionTick(t => t + 1);
        }
    }, [adapter, sessionId, playerId, refetch]);

    const markReady = useCallback(async () => {
        try {
            await adapter.setReady(sessionId, playerId);
            setError(null);
        } catch (e) {
            setError(toAdapterError(e, "usePreparation:markReady"));
        } finally {
            setActionTick(t => t + 1);
        }
    }, [adapter, sessionId, playerId]);

    return {
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
        allPlaced: computeAllPlaced(ships, field),
        loading,
        error,
        actionTick
    };
}
