import {useRef, useState} from "react";
import {GameAdapterError} from "../adapters/AdapterErrors";
import {useSessionEvents} from "./useSessionEvents";
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
 * Tracks opponent presence and session stage while waiting for a second player to join.
 *
 * Driven by {@link useSessionEvents}'s pushed snapshots rather than polling: each received
 * payload's `opponent`/`gameStage` are applied directly. Once the stage reaches
 * `WAIT_OVER_STAGES` (`PREPARATION`, `IN_GAME`, or `FINISHED`), further pushes are ignored via
 * a ref flag (`doneRef`) so a stray late event can't revive a stale "still waiting" state.
 *
 * @param sessionId - ID of the game session.
 * @param playerId - ID of the current player within that session.
 * @returns Opponent/stage snapshot and loading/error flags — see {@link WaitRoomState}.
 */
export function useWaitRoom(sessionId: string, playerId: string): WaitRoomState {
    const [opponent, setOpponent] = useState<ResponseOpponentInformationDto | null>(null);
    const [stage, setStage] = useState<string | null>(null);
    const [loading, setLoading] = useState<boolean>(true);
    const [error] = useState<GameAdapterError | null>(null);

    // Tracks whether the wait is over so a stray late push can't revive "still waiting" state.
    const doneRef = useRef(false);

    useSessionEvents(sessionId, playerId, payload => {
        if (doneRef.current) {
            return;
        }
        setStage(payload.gameStage);
        setOpponent(payload.opponent ?? {playerName: "", ready: false});
        setLoading(false);

        if (WAIT_OVER_STAGES.has(payload.gameStage)) {
            doneRef.current = true;
        }
    });

    return {opponent, stage, loading, error};
}
