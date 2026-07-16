import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act, renderHook, waitFor} from "@testing-library/react";
import type {ReactNode} from "react";
import {MockGameAdapter} from "../adapters/MockGameAdapter";
import {GameAdapterProvider} from "../adapters/GameAdapterContext";
import {useGameplay} from "./useGameplay";
import type {Coordinate, ShipDto} from "../logic/ApplicationTypes";

function makeWrapper(adapter: MockGameAdapter) {
    return function Wrapper({children}: { children: ReactNode }) {
        return <GameAdapterProvider adapter={adapter}>{children}</GameAdapterProvider>;
    };
}

// Places each ship VERTICAL, round-robining across 5 columns (0,2,4,6,8 — every other column,
// so neighbouring ships never touch diagonally) and stacking down each column with a 1-row gap
// between ships (also enough to avoid diagonal touching, since the moat is only 1 cell wide).
// Verified by construction to keep every UKRAINIAN ship (sizes 1,1,1,1,2,2,2,3,3,4) within the
// 10x10 board with no moat collisions.
async function placeAllShips(adapter: MockGameAdapter, sessionId: string, playerId: string, ships: ShipDto[]): Promise<Coordinate[]> {
    const columns = [0, 2, 4, 6, 8];
    const nextRowByColumn = new Map<number, number>(columns.map(c => [c, 0]));
    const occupiedCells: Coordinate[] = [];
    for (let i = 0; i < ships.length; i++) {
        const column = columns[i % columns.length];
        const row = nextRowByColumn.get(column)!;
        await adapter.addShip(sessionId, playerId, ships[i].shipId, {row, column}, "VERTICAL");
        for (let r = row; r < row + ships[i].shipSize; r++) {
            occupiedCells.push({row: r, column});
        }
        nextRowByColumn.set(column, row + ships[i].shipSize + 1);
    }
    return occupiedCells;
}

/**
 * Sets up a session where both players are placed+ready, so stage is IN_GAME and p1 goes
 * first. Also returns every cell occupied by p2's (the opponent's) ships, so tests can shoot
 * deterministically at real ship cells without ever missing (a miss would pass the turn away
 * from p1, per MockGameAdapter's classic-Battleship turn rule).
 */
async function setUpInGameSession(adapter: MockGameAdapter) {
    const sessionId = await adapter.createSession("UKRAINIAN");
    const p1 = await adapter.createPlayer(sessionId, "Alice");
    const p2 = await adapter.createPlayer(sessionId, "Bob");

    const p1Prep = await adapter.getPreparationState(sessionId, p1.playerId);
    await placeAllShips(adapter, sessionId, p1.playerId, p1Prep.ships);
    await adapter.setReady(sessionId, p1.playerId);

    const p2Prep = await adapter.getPreparationState(sessionId, p2.playerId);
    const p2OccupiedCells = await placeAllShips(adapter, sessionId, p2.playerId, p2Prep.ships);
    await adapter.setReady(sessionId, p2.playerId);

    return {sessionId, p1: p1.playerId, p2: p2.playerId, p2OccupiedCells};
}

describe("useGameplay", () => {
    beforeEach(() => {
        vi.useFakeTimers({shouldAdvanceTime: true});
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it("loads game state on mount", async () => {
        const adapter = new MockGameAdapter();
        const {sessionId, p1} = await setUpInGameSession(adapter);

        const {result} = renderHook(() => useGameplay(sessionId, p1), {
            wrapper: makeWrapper(adapter)
        });

        await waitFor(() => expect(result.current.loading).toBe(false));
        expect(result.current.state?.playerName).toBe("Alice");
        expect(result.current.state?.isPlayerActive).toBe(true);
        expect(result.current.error).toBeNull();
    });

    it("shoot updates state immediately and returns the shot result", async () => {
        const adapter = new MockGameAdapter();
        const {sessionId, p1} = await setUpInGameSession(adapter);

        const {result} = renderHook(() => useGameplay(sessionId, p1), {
            wrapper: makeWrapper(adapter)
        });

        await waitFor(() => expect(result.current.loading).toBe(false));

        // Opponent's ships all live in column 0 (rows 0..9); shooting column 5 is a guaranteed miss.
        let shotResult: string | null = null;
        await act(async () => {
            shotResult = await result.current.shoot({row: 0, column: 5});
        });

        expect(shotResult).toBe("MISS");
        // State refetched immediately after the shot: opponent field shows the shot cell.
        expect(result.current.state?.opponentField[0][5].hasShot).toBe(true);
    });

    it("shoot returns null and sets error on failure", async () => {
        const adapter = new MockGameAdapter();
        const {sessionId, p1} = await setUpInGameSession(adapter);

        const {result} = renderHook(() => useGameplay(sessionId, p1), {
            wrapper: makeWrapper(adapter)
        });

        await waitFor(() => expect(result.current.loading).toBe(false));

        let shotResult: string | null = "unset";
        await act(async () => {
            shotResult = await result.current.shoot({row: -1, column: -1});
        });

        expect(shotResult).toBeNull();
        expect(result.current.error).not.toBeNull();
        expect(result.current.error?.errorCode).toBe("COORDINATE_INVALID");
    });

    it("stops polling once hasWinner becomes true", async () => {
        const adapter = new MockGameAdapter();
        const {sessionId, p1, p2OccupiedCells} = await setUpInGameSession(adapter);

        const getGameStateSpy = vi.spyOn(adapter, "getGameState");

        const {result} = renderHook(() => useGameplay(sessionId, p1), {
            wrapper: makeWrapper(adapter)
        });

        await waitFor(() => expect(result.current.loading).toBe(false));

        // Shoot every cell actually occupied by an opponent ship (never a miss), destroying
        // every opponent ship. Turn stays with p1 throughout, since MockGameAdapter only passes
        // the turn to the opponent on a MISS (classic Battleship rule) — misses never happen here.
        for (const cell of p2OccupiedCells) {
            if (result.current.state?.hasWinner) break;
            await act(async () => {
                await result.current.shoot(cell);
            });
        }

        await waitFor(() => expect(result.current.state?.hasWinner).toBe(true));

        const callsAtWin = getGameStateSpy.mock.calls.length;

        await act(async () => {
            await vi.advanceTimersByTimeAsync(20000);
        });

        expect(getGameStateSpy.mock.calls.length).toBe(callsAtWin);
    });
});
