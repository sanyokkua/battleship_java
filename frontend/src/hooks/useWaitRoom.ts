import {useCallback, useRef, useState} from "react";
import {useGameAdapter} from "../adapters/GameAdapterContext";
import {GameAdapterError, isGameAdapterError} from "../adapters/AdapterErrors";
import {usePolling} from "./usePolling";
import type {ResponseOpponentInformationDto} from "../logic/ApplicationTypes";

export type WaitRoomState = {
    opponent: ResponseOpponentInformationDto | null;
    stage: string | null;
    loading: boolean;
    error: GameAdapterError | null;
};

// Stages at or after PREPARATION mean the wait is over — no point polling further.
const WAIT_OVER_STAGES = new Set(["PREPARATION", "IN_GAME", "FINISHED"]);

export function useWaitRoom(sessionId: string, playerId: string): WaitRoomState {
    const adapter = useGameAdapter();
    const [opponent, setOpponent] = useState<ResponseOpponentInformationDto | null>(null);
    const [stage, setStage] = useState<string | null>(null);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<GameAdapterError | null>(null);

    // Tracks whether the wait is over so `usePolling`'s `enabled` flag can flip
    // off without waiting for a re-render round-trip inside the poll tick itself.
    const doneRef = useRef(false);
    const [enabled, setEnabled] = useState(true);

    const tick = useCallback(async () => {
        if (doneRef.current) {
            return;
        }
        try {
            const [opponentInfo, stageValue] = await Promise.all([
                adapter.getOpponent(sessionId, playerId),
                adapter.getStage(sessionId)
            ]);
            setOpponent(opponentInfo);
            setStage(stageValue);
            setError(null);

            if (WAIT_OVER_STAGES.has(stageValue)) {
                doneRef.current = true;
                setEnabled(false);
            }
        } catch (e) {
            setError(isGameAdapterError(e) ? e : new GameAdapterError("Failed to poll wait room", {cause: e, context: "useWaitRoom"}));
        } finally {
            setLoading(false);
        }
    }, [adapter, sessionId, playerId]);

    usePolling(tick, 3000, enabled);

    return {opponent, stage, loading, error};
}
