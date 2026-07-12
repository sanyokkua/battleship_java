import {afterEach, describe, expect, it, vi} from "vitest";
import {renderHook} from "@testing-library/react";
import * as GameBrowserStorage from "../services/GameBrowserStorage";
import {useSessionGuard} from "./useSessionGuard";

describe("useSessionGuard", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("reads session/player/stage through from GameBrowserStorage", () => {
        vi.spyOn(GameBrowserStorage, "loadSession").mockReturnValue("session-123");
        vi.spyOn(GameBrowserStorage, "loadPlayer").mockReturnValue({playerId: "p1", playerName: "Alice"});
        vi.spyOn(GameBrowserStorage, "loadStage").mockReturnValue("PREPARATION");

        const {result} = renderHook(() => useSessionGuard());

        expect(result.current).toEqual({
            sessionId: "session-123",
            player: {playerId: "p1", playerName: "Alice"},
            stage: "PREPARATION"
        });
    });

    it("normalizes an empty-string session to null", () => {
        vi.spyOn(GameBrowserStorage, "loadSession").mockReturnValue("");
        vi.spyOn(GameBrowserStorage, "loadPlayer").mockReturnValue(null);
        vi.spyOn(GameBrowserStorage, "loadStage").mockReturnValue(null);

        const {result} = renderHook(() => useSessionGuard());

        expect(result.current.sessionId).toBeNull();
        expect(result.current.player).toBeNull();
        expect(result.current.stage).toBeNull();
    });

    it("reflects localStorage changes made between renders instead of caching the first read (regression: StageGuard fiber reuse across route transitions)", () => {
        vi.spyOn(GameBrowserStorage, "loadSession").mockReturnValue("session-123");
        vi.spyOn(GameBrowserStorage, "loadPlayer").mockReturnValue({playerId: "p1", playerName: "Alice"});
        vi.spyOn(GameBrowserStorage, "loadStage").mockReturnValue("WAITING_FOR_PLAYERS");

        const {result, rerender} = renderHook(() => useSessionGuard());

        expect(result.current.stage).toBe("WAITING_FOR_PLAYERS");

        // Simulate the server advancing the stage and the screen persisting it to
        // localStorage, without unmounting the component (mirrors React Router
        // reusing the same StageGuard fiber across consecutive gated transitions).
        vi.spyOn(GameBrowserStorage, "loadStage").mockReturnValue("PREPARATION");

        rerender();

        expect(result.current.stage).toBe("PREPARATION");
    });
});
