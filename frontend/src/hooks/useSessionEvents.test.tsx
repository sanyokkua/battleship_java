import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act, render, renderHook} from "@testing-library/react";
import type {ReactNode} from "react";
import {StrictMode} from "react";
import {useSessionEvents} from "./useSessionEvents";
import {GameAdapterProvider} from "../adapters/GameAdapterContext";
import type {GameAdapter} from "../adapters/GameAdapter";
import type {ResponseSessionPushDto} from "../logic/ApplicationTypes";

function makePayload(overrides: Partial<ResponseSessionPushDto> = {}): ResponseSessionPushDto {
    return {gameStage: "PREPARATION", lastUpdate: "t1", opponent: null, gameplayState: null, ...overrides};
}

function makeAdapter(subscribeToSessionEvents: GameAdapter["subscribeToSessionEvents"]): GameAdapter {
    return {subscribeToSessionEvents} as GameAdapter;
}

function makeWrapper(adapter: GameAdapter) {
    return function Wrapper({children}: { children: ReactNode }) {
        return <GameAdapterProvider adapter={adapter}>{children}</GameAdapterProvider>;
    };
}

describe("useSessionEvents", () => {
    it("subscribes on mount and forwards received payloads to onEvent", () => {
        let onEvent: ((p: ResponseSessionPushDto) => void) | null = null;
        const subscribe = vi.fn((_sessionId: string, _playerId: string, cb: (p: ResponseSessionPushDto) => void) => {
            onEvent = cb;
            return vi.fn();
        });
        const adapter = makeAdapter(subscribe);
        const received: ResponseSessionPushDto[] = [];

        renderHook(() => useSessionEvents("s1", "p1", payload => received.push(payload)), {
            wrapper: makeWrapper(adapter)
        });

        expect(subscribe).toHaveBeenCalledWith("s1", "p1", expect.any(Function));

        const payload = makePayload({gameStage: "IN_GAME"});
        onEvent!(payload);

        expect(received).toEqual([payload]);
    });

    it("always calls the latest onEvent reference without resubscribing", () => {
        let onEvent: ((p: ResponseSessionPushDto) => void) | null = null;
        const subscribe = vi.fn((_sessionId: string, _playerId: string, cb: (p: ResponseSessionPushDto) => void) => {
            onEvent = cb;
            return vi.fn();
        });
        const adapter = makeAdapter(subscribe);

        const calls: string[] = [];
        const onEventA = () => calls.push("A");
        const onEventB = () => calls.push("B");

        const {rerender} = renderHook(({fn}: { fn: (p: ResponseSessionPushDto) => void }) =>
            useSessionEvents("s1", "p1", fn), {
            wrapper: makeWrapper(adapter),
            initialProps: {fn: onEventA}
        });
        expect(subscribe).toHaveBeenCalledTimes(1);

        // Re-render with a new onEvent reference but the same sessionId/playerId — must NOT
        // resubscribe.
        rerender({fn: onEventB});
        expect(subscribe).toHaveBeenCalledTimes(1);

        onEvent!(makePayload());
        // The single, still-active subscription now calls the latest onEvent (B), not the stale A.
        expect(calls).toEqual(["B"]);
    });

    it("unsubscribes on unmount", () => {
        const unsubscribe = vi.fn();
        const subscribe = vi.fn(() => unsubscribe);
        const adapter = makeAdapter(subscribe);

        const {unmount} = renderHook(() => useSessionEvents("s1", "p1", vi.fn()), {wrapper: makeWrapper(adapter)});
        expect(unsubscribe).not.toHaveBeenCalled();

        unmount();
        expect(unsubscribe).toHaveBeenCalledTimes(1);
    });

    it("unsubscribes from the old (sessionId, playerId) and resubscribes to the new one on change", () => {
        const unsubscribeA = vi.fn();
        const unsubscribeB = vi.fn();
        const subscribe = vi.fn((sessionId: string) => (sessionId === "s1" ? unsubscribeA : unsubscribeB));
        const adapter = makeAdapter(subscribe);

        const {rerender} = renderHook(({sessionId}: { sessionId: string }) =>
            useSessionEvents(sessionId, "p1", vi.fn()), {
            wrapper: makeWrapper(adapter),
            initialProps: {sessionId: "s1"}
        });
        expect(subscribe).toHaveBeenCalledTimes(1);

        rerender({sessionId: "s2"});

        expect(unsubscribeA).toHaveBeenCalledTimes(1);
        expect(subscribe).toHaveBeenCalledTimes(2);
        expect(subscribe).toHaveBeenLastCalledWith("s2", "p1", expect.any(Function));
    });

    it("does not subscribe while sessionId or playerId is empty", () => {
        const subscribe = vi.fn(() => vi.fn());
        const adapter = makeAdapter(subscribe);

        renderHook(() => useSessionEvents("", "p1", vi.fn()), {wrapper: makeWrapper(adapter)});
        renderHook(() => useSessionEvents("s1", "", vi.fn()), {wrapper: makeWrapper(adapter)});

        expect(subscribe).not.toHaveBeenCalled();
    });

    it("does not leak a duplicate subscription under StrictMode's double-effect-invoke", () => {
        const unsubscribe = vi.fn();
        const subscribe = vi.fn(() => unsubscribe);
        const adapter = makeAdapter(subscribe);

        function Harness() {
            useSessionEvents("s1", "p1", vi.fn());
            return null;
        }

        render(
            <StrictMode>
                <GameAdapterProvider adapter={adapter}>
                    <Harness/>
                </GameAdapterProvider>
            </StrictMode>
        );

        // StrictMode mounts, cleans up, and re-mounts the effect once in dev — exactly one
        // subscription must remain live (the discarded first mount's already unsubscribed).
        expect(subscribe).toHaveBeenCalledTimes(2);
        expect(unsubscribe).toHaveBeenCalledTimes(1);
    });
});

/** Overrides `document.visibilityState` (getter-only in jsdom) for visibility-triggered tests. */
function setVisibilityState(state: DocumentVisibilityState): void {
    Object.defineProperty(document, "visibilityState", {configurable: true, get: () => state});
}

describe("useSessionEvents stale-fallback", () => {
    beforeEach(() => {
        vi.useFakeTimers({shouldAdvanceTime: true});
        setVisibilityState("visible");
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it("with no refetch passed, advancing past staleAfterMs does nothing (no crash, onEvent not called again)", async () => {
        const subscribe = vi.fn(() => vi.fn());
        const adapter = makeAdapter(subscribe);
        const onEvent = vi.fn();

        renderHook(() => useSessionEvents("s1", "p1", onEvent, undefined, 1000), {
            wrapper: makeWrapper(adapter)
        });

        expect(onEvent).not.toHaveBeenCalled();

        await act(async () => {
            await vi.advanceTimersByTimeAsync(5000);
        });

        expect(onEvent).not.toHaveBeenCalled();
    });

    it("calls refetch and forwards its resolved payload to onEvent once no real event has arrived for staleAfterMs", async () => {
        const subscribe = vi.fn(() => vi.fn());
        const adapter = makeAdapter(subscribe);
        const onEvent = vi.fn();
        const payload = makePayload({gameStage: "IN_GAME"});
        const refetch = vi.fn().mockResolvedValue(payload);

        renderHook(() => useSessionEvents("s1", "p1", onEvent, refetch, 1000), {
            wrapper: makeWrapper(adapter)
        });

        await act(async () => {
            await vi.advanceTimersByTimeAsync(5000);
        });

        expect(refetch).toHaveBeenCalledTimes(1);
        expect(onEvent).toHaveBeenCalledWith(payload);
    });

    it("resets the staleness clock when a real push event arrives, so a subsequent tick under the new staleAfterMs window does not trigger refetch", async () => {
        let pushEvent: ((p: ResponseSessionPushDto) => void) | null = null;
        const subscribe = vi.fn((_sessionId: string, _playerId: string, cb: (p: ResponseSessionPushDto) => void) => {
            pushEvent = cb;
            return vi.fn();
        });
        const adapter = makeAdapter(subscribe);
        const onEvent = vi.fn();
        const refetch = vi.fn().mockResolvedValue(makePayload());

        // staleAfterMs (7000) is intentionally just above the fixed 5000ms interval tick
        // resolution, so this test spans two ticks. `lastEventAtRef` starts at 0 (epoch), so an
        // initial priming push is needed to establish a real baseline before staleness can be
        // measured meaningfully.
        renderHook(() => useSessionEvents("s1", "p1", onEvent, refetch, 7000), {
            wrapper: makeWrapper(adapter)
        });

        act(() => {
            pushEvent!(makePayload({lastUpdate: "initial-push"}));
        });

        // First interval tick: 5000ms since the initial push, under the 7000ms threshold — no
        // refetch.
        await act(async () => {
            await vi.advanceTimersByTimeAsync(5000);
        });
        expect(refetch).not.toHaveBeenCalled();

        // A second real push arrives right at the first tick, resetting the staleness clock.
        act(() => {
            pushEvent!(makePayload({lastUpdate: "real-push"}));
        });

        // Second interval tick: only 5000ms since the *second* push (still under 7000ms), even
        // though 10000ms have elapsed since the initial push — must NOT trigger a refetch. Without
        // the reset, staleness would be measured from the initial push (10000ms, over threshold)
        // and would incorrectly trigger.
        await act(async () => {
            await vi.advanceTimersByTimeAsync(5000);
        });

        expect(refetch).not.toHaveBeenCalled();
    });

    it("does not fire overlapping refetch calls when the interval tick and a manual refresh() overlap", async () => {
        const subscribe = vi.fn(() => vi.fn());
        const adapter = makeAdapter(subscribe);
        const onEvent = vi.fn();
        let resolveRefetch: ((p: ResponseSessionPushDto) => void) | null = null;
        const refetch = vi.fn(() => new Promise<ResponseSessionPushDto>(resolve => {
            resolveRefetch = resolve;
        }));

        const {result} = renderHook(() => useSessionEvents("s1", "p1", onEvent, refetch, 1000), {
            wrapper: makeWrapper(adapter)
        });

        // Trigger the interval's stale check (ticks every 5000ms), which starts an in-flight
        // refetch...
        await act(async () => {
            await vi.advanceTimersByTimeAsync(5000);
        });
        expect(refetch).toHaveBeenCalledTimes(1);

        // ...then a manual refresh() call while it's still pending must be a no-op.
        await act(async () => {
            await result.current.refresh();
        });
        expect(refetch).toHaveBeenCalledTimes(1);

        // Resolve the first call; refetch should be callable again afterwards.
        await act(async () => {
            resolveRefetch!(makePayload());
        });
    });

    it("triggers an immediate refetch when the document becomes visible, regardless of recency of the last event", async () => {
        let pushEvent: ((p: ResponseSessionPushDto) => void) | null = null;
        const subscribe = vi.fn((_sessionId: string, _playerId: string, cb: (p: ResponseSessionPushDto) => void) => {
            pushEvent = cb;
            return vi.fn();
        });
        const adapter = makeAdapter(subscribe);
        const onEvent = vi.fn();
        const refetch = vi.fn().mockResolvedValue(makePayload({lastUpdate: "from-refetch"}));

        renderHook(() => useSessionEvents("s1", "p1", onEvent, refetch, 20000), {
            wrapper: makeWrapper(adapter)
        });

        // A real push just arrived, so the interval wouldn't consider us stale.
        act(() => {
            pushEvent!(makePayload({lastUpdate: "fresh-push"}));
        });

        setVisibilityState("visible");
        await act(async () => {
            document.dispatchEvent(new Event("visibilitychange"));
            await Promise.resolve();
        });

        expect(refetch).toHaveBeenCalledTimes(1);
        expect(onEvent).toHaveBeenLastCalledWith(makePayload({lastUpdate: "from-refetch"}));
    });

    it("refresh() calls refetch and forwards its resolved payload to onEvent, usable directly like a manual button", async () => {
        const subscribe = vi.fn(() => vi.fn());
        const adapter = makeAdapter(subscribe);
        const onEvent = vi.fn();
        const payload = makePayload({gameStage: "FINISHED"});
        const refetch = vi.fn().mockResolvedValue(payload);

        const {result} = renderHook(() => useSessionEvents("s1", "p1", onEvent, refetch, 20000), {
            wrapper: makeWrapper(adapter)
        });

        await act(async () => {
            await result.current.refresh();
        });

        expect(refetch).toHaveBeenCalledTimes(1);
        expect(onEvent).toHaveBeenCalledWith(payload);
    });
});
