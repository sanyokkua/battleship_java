import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act, renderHook, waitFor} from "@testing-library/react";
import type {ReactNode} from "react";
import {MockGameAdapter} from "../adapters/MockGameAdapter";
import {GameAdapterProvider} from "../adapters/GameAdapterContext";
import {GameAdapterError} from "../adapters/AdapterErrors";
import {usePreparation} from "./usePreparation";
import type {Coordinate, ShipDto} from "../logic/ApplicationTypes";

function makeWrapper(adapter: MockGameAdapter) {
    return function Wrapper({children}: { children: ReactNode }) {
        return <GameAdapterProvider adapter={adapter}>{children}</GameAdapterProvider>;
    };
}

async function setUpTwoPlayerSession(adapter: MockGameAdapter, edition: string = "UKRAINIAN") {
    const sessionId = await adapter.createSession(edition);
    const p1 = await adapter.createPlayer(sessionId, "Alice");
    const p2 = await adapter.createPlayer(sessionId, "Bob");
    return {sessionId, p1: p1.playerId, p2: p2.playerId};
}

/**
 * Assigns each ship in `ships` a VERTICAL placement that never collides with any other ship's
 * moat: round-robins across 5 columns (0,2,4,6,8 — every other column, so neighbouring ships
 * never touch diagonally) and stacks down each column with a 1-row gap between ships (also
 * enough to avoid diagonal touching, since the moat is only 1 cell wide). Works for any ship
 * size sequence that fits — in particular both UKRAINIAN editions used in these tests.
 */
function nonCollidingPlacements(ships: ShipDto[]): Map<string, Coordinate> {
    const columns = [0, 2, 4, 6, 8];
    const nextRowByColumn = new Map<number, number>(columns.map(c => [c, 0]));
    const placements = new Map<string, Coordinate>();
    ships.forEach((ship, i) => {
        const column = columns[i % columns.length];
        const row = nextRowByColumn.get(column)!;
        placements.set(ship.shipId, {row, column});
        nextRowByColumn.set(column, row + ship.shipSize + 1);
    });
    return placements;
}

describe("usePreparation", () => {
    beforeEach(() => {
        vi.useFakeTimers({shouldAdvanceTime: true});
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it("loads initial ships/field on mount", async () => {
        const adapter = new MockGameAdapter();
        const {sessionId, p1} = await setUpTwoPlayerSession(adapter);

        const {result} = renderHook(() => usePreparation(sessionId, p1), {
            wrapper: makeWrapper(adapter)
        });

        await waitFor(() => expect(result.current.loading).toBe(false));

        expect(result.current.ships.length).toBe(10); // UKRAINIAN edition ship count
        expect(result.current.field.length).toBe(10);
        expect(result.current.allPlaced).toBe(false);
        expect(result.current.error).toBeNull();
    });

    it("placeShip updates field/ships and renders the moat; removeShipAt undoes it", async () => {
        const adapter = new MockGameAdapter();
        const {sessionId, p1} = await setUpTwoPlayerSession(adapter);

        const {result} = renderHook(() => usePreparation(sessionId, p1), {
            wrapper: makeWrapper(adapter)
        });

        await waitFor(() => expect(result.current.loading).toBe(false));

        const oneCellShip = result.current.ships.find(s => s.shipSize === 1)!;
        const initialShipCount = result.current.ships.length;

        await result.current.placeShip(oneCellShip.shipId, {row: 5, column: 5}, "HORIZONTAL");

        await waitFor(() => {
            expect(result.current.ships.length).toBe(initialShipCount - 1);
        });
        expect(result.current.ships.some(s => s.shipId === oneCellShip.shipId)).toBe(false);
        expect(result.current.field[5][5].ship?.shipId).toBe(oneCellShip.shipId);
        // Moat: neighbouring cell must be unavailable now.
        expect(result.current.field[4][5].isAvailable).toBe(false);
        expect(result.current.field[4][5].ship).toBeNull();
        expect(result.current.error).toBeNull();

        await result.current.removeShipAt({row: 5, column: 5});

        await waitFor(() => {
            expect(result.current.ships.some(s => s.shipId === oneCellShip.shipId)).toBe(true);
        });
        expect(result.current.field[5][5].ship).toBeNull();
        expect(result.current.field[4][5].isAvailable).toBe(true);
    });

    it("allPlaced becomes true once every ship has been placed", async () => {
        const adapter = new MockGameAdapter();
        const {sessionId, p1} = await setUpTwoPlayerSession(adapter);

        const {result} = renderHook(() => usePreparation(sessionId, p1), {
            wrapper: makeWrapper(adapter)
        });

        await waitFor(() => expect(result.current.loading).toBe(false));
        expect(result.current.allPlaced).toBe(false);

        // Place every ship using a non-colliding VERTICAL layout (see nonCollidingPlacements).
        const shipsToPlace = [...result.current.ships];
        const placements = nonCollidingPlacements(shipsToPlace);
        for (const ship of shipsToPlace) {
            await result.current.placeShip(ship.shipId, placements.get(ship.shipId)!, "VERTICAL");
        }

        await waitFor(() => expect(result.current.allPlaced).toBe(true));
        expect(result.current.ships.length).toBe(0);
    });

    it("sets error on an invalid placeShip call and leaves state unchanged", async () => {
        const adapter = new MockGameAdapter();
        const {sessionId, p1} = await setUpTwoPlayerSession(adapter);

        const {result} = renderHook(() => usePreparation(sessionId, p1), {
            wrapper: makeWrapper(adapter)
        });

        await waitFor(() => expect(result.current.loading).toBe(false));
        const shipsBefore = result.current.ships;
        const fieldBefore = result.current.field;

        await result.current.placeShip("non-existent-ship-id", {row: 0, column: 0}, "HORIZONTAL");

        await waitFor(() => expect(result.current.error).not.toBeNull());
        expect(result.current.error?.errorCode).toBe("SHIP_ID_INVALID");
        // State (ships/field) must be left unchanged on failure.
        expect(result.current.ships).toBe(shipsBefore);
        expect(result.current.field).toBe(fieldBefore);
    });

    it("polls opponent readiness every 3s and derives opponentReady", async () => {
        const adapter = new MockGameAdapter();
        const {sessionId, p1, p2} = await setUpTwoPlayerSession(adapter);

        const {result} = renderHook(() => usePreparation(sessionId, p1), {
            wrapper: makeWrapper(adapter)
        });

        await waitFor(() => expect(result.current.loading).toBe(false));
        expect(result.current.opponentReady).toBe(false);

        // Ready-up the opponent (player 2) by placing all their ships then calling setReady.
        const opponentPrep = await adapter.getPreparationState(sessionId, p2);
        const opponentPlacements = nonCollidingPlacements(opponentPrep.ships);
        for (const ship of opponentPrep.ships) {
            await adapter.addShip(sessionId, p2, ship.shipId, opponentPlacements.get(ship.shipId)!, "VERTICAL");
        }
        await adapter.setReady(sessionId, p2);

        await vi.advanceTimersByTimeAsync(3000);

        await waitFor(() => expect(result.current.opponentReady).toBe(true));
    });

    it("refresh() calls through to getStage/getOpponent and applies the result like a push, without touching ships/field", async () => {
        const adapter = new MockGameAdapter();
        const {sessionId, p1, p2} = await setUpTwoPlayerSession(adapter);

        const {result} = renderHook(() => usePreparation(sessionId, p1), {
            wrapper: makeWrapper(adapter)
        });

        await waitFor(() => expect(result.current.loading).toBe(false));
        expect(result.current.opponentReady).toBe(false);

        const shipsBefore = result.current.ships;
        const fieldBefore = result.current.field;

        // Ready-up the opponent directly via the adapter (bypassing the push channel), then
        // confirm refresh() picks it up on demand.
        const opponentPrep = await adapter.getPreparationState(sessionId, p2);
        const opponentPlacements = nonCollidingPlacements(opponentPrep.ships);
        for (const ship of opponentPrep.ships) {
            await adapter.addShip(sessionId, p2, ship.shipId, opponentPlacements.get(ship.shipId)!, "VERTICAL");
        }
        await adapter.setReady(sessionId, p2);

        const getStageSpy = vi.spyOn(adapter, "getStage");
        const getOpponentSpy = vi.spyOn(adapter, "getOpponent");

        await act(async () => {
            await result.current.refresh();
        });

        expect(getStageSpy).toHaveBeenCalledWith(sessionId);
        expect(getOpponentSpy).toHaveBeenCalledWith(sessionId, p1);
        expect(result.current.opponentReady).toBe(true);
        expect(result.current.stage).toBe("PREPARATION");
        // ships/field are the unrelated mount-time refetch's concern only — a refresh() call
        // must not touch them.
        expect(result.current.ships).toBe(shipsBefore);
        expect(result.current.field).toBe(fieldBefore);
    });

    it("refresh() still resolves without applying opponentReady when getOpponent rejects with OPPONENT_NOT_FOUND", async () => {
        const adapter = new MockGameAdapter();
        const {sessionId, p1} = await setUpTwoPlayerSession(adapter);

        const {result} = renderHook(() => usePreparation(sessionId, p1), {
            wrapper: makeWrapper(adapter)
        });

        await waitFor(() => expect(result.current.loading).toBe(false));

        vi.spyOn(adapter, "getOpponent").mockRejectedValueOnce(
            new GameAdapterError("no opponent yet", {errorCode: "OPPONENT_NOT_FOUND"})
        );

        await act(async () => {
            await result.current.refresh();
        });

        // opponent is null, so the push handler's `if (payload.opponent)` guard skips the
        // update entirely — opponentReady stays at its previous (false) value, no crash/throw.
        // stage is unconditional in the push handler, so it still gets applied from the real
        // (unmocked) getStage() call.
        expect(result.current.opponentReady).toBe(false);
        expect(result.current.stage).toBe("PREPARATION");
    });
});
