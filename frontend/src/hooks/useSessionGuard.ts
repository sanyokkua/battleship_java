import {useState} from "react";
import {loadPlayer, loadSession, loadStage} from "../services/GameBrowserStorage";
import type {ResponseCreatedPlayerDto} from "../logic/ApplicationTypes";

export type SessionGuardState = {
    sessionId: string | null;
    player: ResponseCreatedPlayerDto | null;
    stage: string | null;
};

function readSessionGuardState(): SessionGuardState {
    const rawSessionId = loadSession();
    return {
        sessionId: rawSessionId ? rawSessionId : null,
        player: loadPlayer(),
        stage: loadStage()
    };
}

/**
 * Reads persisted session/player/stage from GameBrowserStorage.
 *
 * Read once on mount (useState initializer) — this is a primitive for a
 * future route-guard hook/component to consume, not a reactive subscription
 * to storage changes from other tabs/windows.
 */
export function useSessionGuard(): SessionGuardState {
    const [state] = useState<SessionGuardState>(readSessionGuardState);
    return state;
}
