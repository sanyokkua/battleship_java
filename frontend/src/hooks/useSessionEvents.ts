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
 * Hardening against a silently-dead SSE connection (the exact bug this fallback exists for): a
 * timestamp of the last received event (real push or successful fallback fetch) is tracked, and
 * a second, independent effect watches for staleness. When `refetch` is provided:
 *   - Every 5s, if no event has landed within `staleAfterMs`, `refetch` is called and its
 *     resolved payload is applied exactly like a pushed payload would be.
 *   - Whenever the tab becomes visible again (Page Visibility API), `refetch` is called
 *     immediately and unconditionally — this is the primary mobile background/foreground
 *     recovery path, since a backgrounded tab's SSE connection is often silently dead by the
 *     time the user returns.
 *   - A manual `refresh()` (returned from this hook, e.g. for a "refresh" button) triggers the
 *     same fallback path on demand.
 *   - An in-flight guard ensures the stale-interval tick, a visibility change, and a manual
 *     `refresh()` call never cause overlapping `refetch` calls.
 *
 * `refetch` and `staleAfterMs` are optional: when `refetch` is omitted, the staleness effect
 * still runs but `refresh()`/the fallback path are no-ops, so the 3-argument call signature
 * keeps behaving exactly as before.
 *
 * @param sessionId - ID of the game session.
 * @param playerId - ID of the subscribing player.
 * @param onEvent - Called with each received snapshot, including the initial one.
 * @param refetch - Optional adapter-call-composed snapshot fetch used as a fallback when no push
 *   event has arrived recently, or when the tab regains visibility. Omit to disable fallback.
 * @param staleAfterMs - How long without a real event before the interval fallback kicks in.
 *   Defaults to 20000 (20s). Ignored when `refetch` is not provided.
 * @returns An object with `refresh()`, an escape hatch that runs the same fallback fetch
 *   on demand (e.g. wired to a manual "refresh" button in later tasks).
 */
export function useSessionEvents(
    sessionId: string,
    playerId: string,
    onEvent: (payload: ResponseSessionPushDto) => void,
    refetch?: () => Promise<ResponseSessionPushDto>,
    staleAfterMs: number = 20000
): { refresh: () => Promise<void> } {
    const adapter = useGameAdapter();
    const onEventRef = useRef(onEvent);
    useLayoutEffect(() => {
        onEventRef.current = onEvent;
    });

    const refetchRef = useRef(refetch);
    useLayoutEffect(() => {
        refetchRef.current = refetch;
    });

    const lastEventAtRef = useRef<number>(0);
    const fetchInFlightRef = useRef(false);

    function applyEvent(payload: ResponseSessionPushDto): void {
        lastEventAtRef.current = Date.now();
        onEventRef.current(payload);
    }

    async function runRefetch(): Promise<void> {
        if (!refetchRef.current || fetchInFlightRef.current) {
            return;
        }
        fetchInFlightRef.current = true;
        try {
            const payload = await refetchRef.current();
            applyEvent(payload);
        } catch (e) {
            console.error("useSessionEvents: fallback refetch failed", e);
        } finally {
            fetchInFlightRef.current = false;
        }
    }

    useEffect(() => {
        if (!sessionId || !playerId) {
            return;
        }
        return adapter.subscribeToSessionEvents(sessionId, playerId, payload => applyEvent(payload));
    }, [adapter, sessionId, playerId]);

    useEffect(() => {
        if (!sessionId || !playerId) {
            return;
        }

        const intervalId = setInterval(() => {
            if (Date.now() - lastEventAtRef.current >= staleAfterMs) {
                void runRefetch();
            }
        }, 5000);

        const onVisibilityChange = () => {
            if (document.visibilityState === "visible") {
                void runRefetch();
            }
        };
        document.addEventListener("visibilitychange", onVisibilityChange);

        return () => {
            clearInterval(intervalId);
            document.removeEventListener("visibilitychange", onVisibilityChange);
        };
        // runRefetch is intentionally omitted: it always reads the latest refetch/onEvent via
        // refs (the same ref-stashing pattern onEventRef uses above), so including it would
        // only force a pointless resubscribe every render without changing behavior.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [sessionId, playerId, staleAfterMs]);

    return {refresh: runRefetch};
}
