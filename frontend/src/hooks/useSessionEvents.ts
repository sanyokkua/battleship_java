import {useEffect, useLayoutEffect, useRef} from "react";
import {useGameAdapter} from "../adapters/GameAdapterContext";
import type {ResponseSessionPushDto} from "../logic/ApplicationTypes";

/**
 * Subscribes to a session/player's push notifications via `GameAdapter.subscribeToSessionEvents`.
 * `onEvent` is called once immediately with the current snapshot (matching the server's
 * snapshot-on-subscribe behavior), then again whenever the session's state changes.
 *
 * `onEvent` is stashed in a ref and always called through it, so callers can pass a fresh closure
 * every render without tearing down/rebuilding the subscription — the effect is keyed only on
 * `[sessionId, playerId]`, not on `onEvent`. This also keeps every `setState` call a caller makes
 * inside `onEvent` reached via an opaque callback rather than written directly in this hook's
 * effect body, avoiding the "derived state via effect" pattern `react-hooks/set-state-in-effect`
 * flags.
 *
 * Opens on mount (and whenever `sessionId`/`playerId` change), and unsubscribes on unmount/
 * dependency change — StrictMode-safe: the first (discarded) mount's subscription is torn down
 * before the second mount's is created.
 *
 * @param sessionId - ID of the game session.
 * @param playerId - ID of the subscribing player.
 * @param onEvent - Called with each received snapshot, including the initial one.
 */
export function useSessionEvents(
    sessionId: string, playerId: string, onEvent: (payload: ResponseSessionPushDto) => void): void {
    const adapter = useGameAdapter();
    const onEventRef = useRef(onEvent);
    useLayoutEffect(() => {
        onEventRef.current = onEvent;
    });

    useEffect(() => {
        if (!sessionId || !playerId) {
            return;
        }
        return adapter.subscribeToSessionEvents(sessionId, playerId, payload => onEventRef.current(payload));
    }, [adapter, sessionId, playerId]);
}
