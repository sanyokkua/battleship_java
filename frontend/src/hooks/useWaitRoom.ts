import {useCallback, useRef, useState} from "react";
import {useGameAdapter} from "../adapters/GameAdapterContext";
import {GameAdapterError, isGameAdapterError} from "../adapters/AdapterErrors";
import {usePolling} from "./usePolling";
import type {ResponseOpponentInformationDto} from "../logic/ApplicationTypes";

/**
 * Return type of {@link useWaitRoom}.
 */
export type WaitRoomState = {
    /** Latest opponent info fetched from the adapter, or `null` before the first successful poll. */
    opponent: ResponseOpponentInformationDto | null;
    /** Latest `GameStage` string fetched from the adapter, or `null` before the first successful poll. */
    stage: string | null;
    /** `true` until the first poll (success or failure) has completed. */
    loading: boolean;
    /** Error from the most recent poll attempt, or `null` if it succeeded. */
    error: GameAdapterError | null;
};

// Stages at or after PREPARATION mean the wait is over — no point polling further.
const WAIT_OVER_STAGES = new Set(["PREPARATION", "IN_GAME", "FINISHED"]);

/**
 * Polls opponent presence and session stage while waiting for a second player to join.
 *
 * Polls every 3s via {@link usePolling}, fetching opponent info and stage concurrently.
 * Once the stage reaches `WAIT_OVER_STAGES` (`PREPARATION`, `IN_GAME`, or `FINISHED`),
 * polling is switched off (via `enabled`) and further ticks are also short-circuited
 * through a ref flag (`doneRef`) so an in-flight tick scheduled just before the switch
 * takes effect is still a no-op.
 *
 * @param sessionId - ID of the game session.
 * @param playerId - ID of the current player within that session.
 * @returns Opponent/stage snapshot and loading/error flags — see {@link WaitRoomState}.
 */
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
            setError(isGameAdapterError(e) ? e : new GameAdapterError("Failed to poll wait room", {
                cause: e,
                context: "useWaitRoom"
            }));
        } finally {
            setLoading(false);
        }
    }, [adapter, sessionId, playerId]);

    usePolling(tick, 3000, enabled);

    return {opponent, stage, loading, error};
}
