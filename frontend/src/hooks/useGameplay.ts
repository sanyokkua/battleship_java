import {useCallback, useRef, useState} from "react";
import {useGameAdapter} from "../adapters/GameAdapterContext";
import {GameAdapterError, isGameAdapterError} from "../adapters/AdapterErrors";
import {usePolling} from "./usePolling";
import type {Coordinate, ResponseGameplayStateDto} from "../logic/ApplicationTypes";

/**
 * Return type of {@link useGameplay}.
 */
export type GameplayHookState = {
    /** Latest gameplay state fetched from the adapter, or `null` before the first successful poll/shot. */
    state: ResponseGameplayStateDto | null;
    /**
     * Fires a shot at `at` for the current player, then refetches gameplay state so `state`
     * reflects the outcome. Resolves to the shot result (`"HIT"`, `"MISS"`, or `"DESTROYED"`),
     * or `null` if the request failed (in which case `error` is also set).
     */
    shoot: (at: Coordinate) => Promise<"HIT" | "MISS" | "DESTROYED" | null>;
    /** `true` until the first poll (success or failure) has completed. */
    loading: boolean;
    /** Error from the most recent poll or shot attempt, or `null` if it succeeded. */
    error: GameAdapterError | null;
};

function toAdapterError(e: unknown, context: string): GameAdapterError {
    return isGameAdapterError(e) ? e : new GameAdapterError("Gameplay request failed", {cause: e, context});
}

/**
 * Polls gameplay state for an in-progress game and exposes a `shoot` action.
 *
 * Polls every 5s via {@link usePolling} while the game has no winner yet; once
 * `state.hasWinner` becomes true, polling is switched off (via `enabled`) and further
 * ticks are also short-circuited through a ref flag (`doneRef`) so an in-flight tick
 * scheduled just before the switch takes effect is still a no-op. `shoot` bypasses the
 * poll cadence entirely — it calls the adapter immediately and refetches state right
 * after, so the UI reflects the shot's outcome without waiting for the next tick.
 *
 * @param sessionId - ID of the game session.
 * @param playerId - ID of the current player within that session.
 * @returns Gameplay state, loading/error flags, and the `shoot` action — see {@link GameplayHookState}.
 */
export function useGameplay(sessionId: string, playerId: string): GameplayHookState {
    const adapter = useGameAdapter();
    const [state, setState] = useState<ResponseGameplayStateDto | null>(null);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<GameAdapterError | null>(null);

    const doneRef = useRef(false);
    const [enabled, setEnabled] = useState(true);

    const refetch = useCallback(async () => {
        const gameState = await adapter.getGameState(sessionId, playerId);
        setState(gameState);
        if (gameState.hasWinner) {
            doneRef.current = true;
            setEnabled(false);
        }
        return gameState;
    }, [adapter, sessionId, playerId]);

    const tick = useCallback(async () => {
        if (doneRef.current) {
            return;
        }
        try {
            await refetch();
            setError(null);
        } catch (e) {
            setError(toAdapterError(e, "useGameplay:poll"));
        } finally {
            setLoading(false);
        }
    }, [refetch]);

    usePolling(tick, 5000, enabled);

    const shoot = useCallback(async (at: Coordinate): Promise<"HIT" | "MISS" | "DESTROYED" | null> => {
        try {
            const result = await adapter.shoot(sessionId, playerId, at);
            await refetch();
            setError(null);
            return result.shotResult as "HIT" | "MISS" | "DESTROYED";
        } catch (e) {
            setError(toAdapterError(e, "useGameplay:shoot"));
            return null;
        }
    }, [adapter, sessionId, playerId, refetch]);

    return {state, shoot, loading, error};
}
