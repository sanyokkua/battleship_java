import {useCallback, useRef, useState} from "react";
import {useGameAdapter} from "../adapters/GameAdapterContext";
import {GameAdapterError, isGameAdapterError} from "../adapters/AdapterErrors";
import {usePolling} from "./usePolling";
import type {Coordinate, ResponseGameplayStateDto} from "../logic/ApplicationTypes";

export type GameplayHookState = {
    state: ResponseGameplayStateDto | null;
    shoot: (at: Coordinate) => Promise<"HIT" | "MISS" | "DESTROYED" | null>; // null if the call failed
    loading: boolean;
    error: GameAdapterError | null;
};

function toAdapterError(e: unknown, context: string): GameAdapterError {
    return isGameAdapterError(e) ? e : new GameAdapterError("Gameplay request failed", {cause: e, context});
}

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
