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
 * Reads fresh on every render — deliberately NOT memoized via useState/useMemo.
 * The read is three cheap synchronous localStorage calls, and callers (notably
 * StageGuard, which React Router can reuse across consecutive route transitions
 * at the same outlet position instead of remounting) need up-to-date data on
 * every render, not a stale value cached from first mount. This is still not a
 * reactive subscription to storage changes from other tabs/windows — it only
 * reflects the value as of whenever the consuming component happens to render.
 */
export function useSessionGuard(): SessionGuardState {
    return readSessionGuardState();
}
