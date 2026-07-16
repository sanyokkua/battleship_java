import {useCallback, useRef, useState} from "react";
import {useGameAdapter} from "../adapters/GameAdapterContext";
import {GameAdapterError, isGameAdapterError} from "../adapters/AdapterErrors";
import {useSessionEvents} from "./useSessionEvents";
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
 * Drives gameplay state for an in-progress game and exposes a `shoot` action.
 *
 * Gameplay state is pushed via {@link useSessionEvents} rather than polled: every received
 * snapshot's `gameplayState` (once the opponent exists and the session is IN_GAME/FINISHED)
 * is applied directly, with no extra round trip. Once `state.hasWinner` becomes true, further
 * pushes are ignored via a ref flag (`doneRef`), so a stray late/duplicate event can't
 * resurrect a stale non-winning state. `shoot` bypasses the push entirely for the *acting*
 * player's own view — it calls the adapter immediately and refetches state right after, so the
 * UI reflects the shot's outcome without waiting for a round trip through the push channel;
 * the push exists to observe the *opponent's* moves.
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

    const applyGameplayState = useCallback((gameState: ResponseGameplayStateDto) => {
        setState(gameState);
        if (gameState.hasWinner) {
            doneRef.current = true;
        }
    }, []);

    useSessionEvents(sessionId, playerId, payload => {
        if (doneRef.current) {
            return;
        }
        setLoading(false);
        if (payload.gameplayState) {
            applyGameplayState(payload.gameplayState);
        }
    });

    const shoot = useCallback(async (at: Coordinate): Promise<"HIT" | "MISS" | "DESTROYED" | null> => {
        try {
            const result = await adapter.shoot(sessionId, playerId, at);
            const gameState = await adapter.getGameState(sessionId, playerId);
            applyGameplayState(gameState);
            setError(null);
            return result.shotResult as "HIT" | "MISS" | "DESTROYED";
        } catch (e) {
            setError(toAdapterError(e, "useGameplay:shoot"));
            return null;
        }
    }, [adapter, sessionId, playerId, applyGameplayState]);

    return {state, shoot, loading, error};
}
