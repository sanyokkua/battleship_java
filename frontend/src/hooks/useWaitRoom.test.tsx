import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act, renderHook, waitFor} from "@testing-library/react";
import type {ReactNode} from "react";
import {MockGameAdapter} from "../adapters/MockGameAdapter";
import {GameAdapterProvider} from "../adapters/GameAdapterContext";
import {useWaitRoom} from "./useWaitRoom";

/**
 * Fake-timers gotcha: MockGameAdapter's methods are async (return Promises,
 * even though they resolve synchronously under the hood). With fake timers,
 * simply calling `vi.advanceTimersByTime` does not automatically flush
 * microtasks, so `await`s inside the hook's poll tick may not have settled
 * by the time assertions run. `vi.useFakeTimers({ shouldAdvanceTime: true })`
 * combined with wrapping timer advances in `await act(async () => {...})`
 * (which flushes microtasks between/after fake-timer ticks) avoids flakiness.
 */

function makeWrapper(adapter: MockGameAdapter) {
    return function Wrapper({children}: { children: ReactNode }) {
        return <GameAdapterProvider adapter={adapter}>{children}</GameAdapterProvider>;
    };
}

describe("useWaitRoom", () => {
    beforeEach(() => {
        vi.useFakeTimers({shouldAdvanceTime: true});
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it("reflects opponent/stage after the initial poll resolves", async () => {
        const adapter = new MockGameAdapter();
        const sessionId = await adapter.createSession("UKRAINIAN");
        const p1 = await adapter.createPlayer(sessionId, "Alice");
        await adapter.createPlayer(sessionId, "Bob");

        const {result} = renderHook(() => useWaitRoom(sessionId, p1.playerId), {
            wrapper: makeWrapper(adapter)
        });

        await waitFor(() => expect(result.current.loading).toBe(false));

        expect(result.current.opponent).toEqual({playerName: "Bob", ready: false});
        // Both players exist immediately, so stage is already PREPARATION.
        expect(result.current.stage).toBe("PREPARATION");
        expect(result.current.error).toBeNull();
    });

    it("fires the first poll immediately without waiting 3s, before a second player joins", async () => {
        const adapter = new MockGameAdapter();
        const sessionId = await adapter.createSession("UKRAINIAN");
        const p1 = await adapter.createPlayer(sessionId, "Alice");

        const {result} = renderHook(() => useWaitRoom(sessionId, p1.playerId), {
            wrapper: makeWrapper(adapter)
        });

        await waitFor(() => expect(result.current.loading).toBe(false));

        expect(result.current.stage).toBe("WAITING_FOR_PLAYERS");
        expect(result.current.opponent).toEqual({playerName: "", ready: false});
    });

    it("stops polling once stage reaches PREPARATION", async () => {
        const adapter = new MockGameAdapter();
        const sessionId = await adapter.createSession("UKRAINIAN");
        const p1 = await adapter.createPlayer(sessionId, "Alice");
        await adapter.createPlayer(sessionId, "Bob");

        const getOpponentSpy = vi.spyOn(adapter, "getOpponent");
        const getStageSpy = vi.spyOn(adapter, "getStage");

        const {result} = renderHook(() => useWaitRoom(sessionId, p1.playerId), {
            wrapper: makeWrapper(adapter)
        });

        await waitFor(() => expect(result.current.stage).toBe("PREPARATION"));

        const callsAfterSettled = getOpponentSpy.mock.calls.length;
        expect(getStageSpy).toHaveBeenCalled();

        await act(async () => {
            await vi.advanceTimersByTimeAsync(10000);
        });

        // No further polling after the wait is over.
        expect(getOpponentSpy.mock.calls.length).toBe(callsAfterSettled);
    });
});
