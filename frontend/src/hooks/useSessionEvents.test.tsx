import {describe, expect, it, vi} from "vitest";
import {render, renderHook} from "@testing-library/react";
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
