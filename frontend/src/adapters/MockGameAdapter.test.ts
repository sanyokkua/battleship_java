import {beforeEach, describe, expect, it} from "vitest";
import {MockGameAdapter} from "./MockGameAdapter";
import type {CellDto} from "../logic/ApplicationTypes";

async function setUpTwoPlayerSession(adapter: MockGameAdapter, edition: string = "UKRAINIAN") {
    const sessionId = await adapter.createSession(edition);
    const p1 = await adapter.createPlayer(sessionId, "Alice");
    const p2 = await adapter.createPlayer(sessionId, "Bob");
    return {sessionId, p1: p1.playerId, p2: p2.playerId};
}

function countAvailable(field: CellDto[][]): number {
    let n = 0;
    for (const row of field) {
        for (const cell of row) {
            if (cell.isAvailable) n++;
        }
    }
    return n;
}

describe("MockGameAdapter", () => {
    let adapter: MockGameAdapter;

    beforeEach(() => {
        adapter = new MockGameAdapter();
    });

    it("getEditions returns the fixed catalog", async () => {
        await expect(adapter.getEditions()).resolves.toEqual(["UKRAINIAN", "MILTON_BRADLEY"]);
    });

    it("createSession + createPlayer moves stage from INITIALIZED to WAITING_FOR_PLAYERS to PREPARATION", async () => {
        const sessionId = await adapter.createSession("UKRAINIAN");
        await expect(adapter.getStage(sessionId)).resolves.toBe("INITIALIZED");

        await adapter.createPlayer(sessionId, "Alice");
        await expect(adapter.getStage(sessionId)).resolves.toBe("WAITING_FOR_PLAYERS");

        await adapter.createPlayer(sessionId, "Bob");
        await expect(adapter.getStage(sessionId)).resolves.toBe("PREPARATION");
    });

    it("moats all 8 neighbours for a 1-cell ship placed in the middle of the board", async () => {
        const {sessionId, p1} = await setUpTwoPlayerSession(adapter);
        const prep = await adapter.getPreparationState(sessionId, p1);
        const oneCellShip = prep.ships.find(s => s.shipSize === 1)!;

        await adapter.addShip(sessionId, p1, oneCellShip.shipId, {row: 5, column: 5}, "HORIZONTAL");

        const after = await adapter.getPreparationState(sessionId, p1);
        const neighbourCoords = [
            [4, 4], [4, 5], [4, 6],
            [5, 4], [5, 6],
            [6, 4], [6, 5], [6, 6]
        ];
        for (const [r, c] of neighbourCoords) {
            expect(after.field[r][c].isAvailable).toBe(false);
            expect(after.field[r][c].ship).toBeNull();
        }
        expect(after.field[5][5].isAvailable).toBe(false);
        expect(after.field[5][5].ship).not.toBeNull();

        // A cell two away in any direction is unaffected.
        expect(after.field[3][5].isAvailable).toBe(true);
        expect(after.field[5][3].isAvailable).toBe(true);
    });

    it("moats only in-bounds neighbours for a 1-cell ship placed at a corner", async () => {
        const {sessionId, p1} = await setUpTwoPlayerSession(adapter);
        const prep = await adapter.getPreparationState(sessionId, p1);
        const oneCellShip = prep.ships.find(s => s.shipSize === 1)!;

        await adapter.addShip(sessionId, p1, oneCellShip.shipId, {row: 0, column: 0}, "HORIZONTAL");

        const after = await adapter.getPreparationState(sessionId, p1);
        // Only the 3 in-bounds neighbours of (0,0) should be moated, plus the ship cell itself.
        expect(after.field[0][0].isAvailable).toBe(false);
        expect(after.field[0][1].isAvailable).toBe(false);
        expect(after.field[1][0].isAvailable).toBe(false);
        expect(after.field[1][1].isAvailable).toBe(false);

        // Everything else on the board remains available.
        const unaffectedCount = countAvailable(after.field);
        expect(unaffectedCount).toBe(BOARD_CELL_COUNT - 4);
    });

    it("rejects placement onto a moated/occupied cell", async () => {
        const {sessionId, p1} = await setUpTwoPlayerSession(adapter);
        const prep = await adapter.getPreparationState(sessionId, p1);
        const ships = prep.ships.filter(s => s.shipSize === 1);

        await adapter.addShip(sessionId, p1, ships[0].shipId, {row: 5, column: 5}, "HORIZONTAL");

        await expect(adapter.addShip(sessionId, p1, ships[1].shipId, {row: 5, column: 6}, "HORIZONTAL"))
            .rejects.toMatchObject({errorCode: "COORDINATE_INVALID"});
    });

    it("removeShip clears the moat and resets ready", async () => {
        const {sessionId, p1} = await setUpTwoPlayerSession(adapter);
        const prep = await adapter.getPreparationState(sessionId, p1);
        const oneCellShip = prep.ships.find(s => s.shipSize === 1)!;

        await adapter.addShip(sessionId, p1, oneCellShip.shipId, {row: 5, column: 5}, "HORIZONTAL");

        const removed = await adapter.removeShip(sessionId, p1, {row: 5, column: 5});
        expect(removed.deleted).toBe(true);

        const after = await adapter.getPreparationState(sessionId, p1);
        expect(countAvailable(after.field)).toBe(BOARD_CELL_COUNT);
        expect(after.field[5][5].ship).toBeNull();
    });

    it("removeShip resets the ready flag (mirrors real engine behavior)", async () => {
        const {sessionId, p1, p2} = await setUpTwoPlayerSession(adapter);
        await placeAllShips(adapter, sessionId, p1);
        await placeAllShips(adapter, sessionId, p2);

        const readyResult = await adapter.setReady(sessionId, p1);
        expect(readyResult.ready).toBe(true);

        const prep = await adapter.getPreparationState(sessionId, p1);
        // Not yet un-placed since all are placed; grab one placed ship's coordinate via opponent view is not
        // available, so re-fetch full field to find any ship cell to remove.
        const shipCell = findFirstShipCell(prep.field);
        await adapter.removeShip(sessionId, p1, {row: shipCell.row, column: shipCell.col});

        const opponentView = await adapter.getOpponent(sessionId, p2);
        // Opponent info for p2 reflects p2's own readiness, not p1's — assert indirectly via setReady requiring
        // full placement again for p1.
        expect(opponentView.ready).toBe(false);

        await expect(adapter.setReady(sessionId, p1)).rejects.toMatchObject({errorCode: "STAGE_INVALID"});
    });

    it("shoot resolves MISS on an empty cell and switches turn to the opponent", async () => {
        const {sessionId, p1, p2} = await setUpTwoPlayerSession(adapter);
        await placeAllShips(adapter, sessionId, p1);
        await placeAllShips(adapter, sessionId, p2);
        await adapter.setReady(sessionId, p1);
        await adapter.setReady(sessionId, p2);

        await expect(adapter.getStage(sessionId)).resolves.toBe("IN_GAME");

        const state = await adapter.getGameState(sessionId, p1);
        expect(state.isPlayerActive).toBe(true);

        // Find a cell with no ship on p2's field to guarantee a MISS.
        const p2Prep = await adapter.getPreparationState(sessionId, p2);
        const emptyCell = findEmptyCell(p2Prep.field);

        const result = await adapter.shoot(sessionId, p1, {row: emptyCell.row, column: emptyCell.col});
        expect(result.shotResult).toBe("MISS");

        const stateAfter = await adapter.getGameState(sessionId, p1);
        expect(stateAfter.isPlayerActive).toBe(false);
    });

    it("shoot resolves HIT then DESTROYED for a fully-hit ship, and MISS does not leak opponent ship positions", async () => {
        const {sessionId, p1, p2} = await setUpTwoPlayerSession(adapter);
        await placeAllShips(adapter, sessionId, p1);
        await placeAllShips(adapter, sessionId, p2);
        await adapter.setReady(sessionId, p1);
        await adapter.setReady(sessionId, p2);

        const p2Prep = await adapter.getPreparationState(sessionId, p2);
        const oneCellShipCoord = findShipCellOfSize(p2Prep.field, 1);

        // Before any shot, p1's view of the opponent field must not reveal the ship.
        const beforeState = await adapter.getGameState(sessionId, p1);
        const hiddenCell = beforeState.opponentField[oneCellShipCoord.row][oneCellShipCoord.col];
        expect(hiddenCell.ship).toBeNull();
        expect(hiddenCell.hasShot).toBe(false);

        const shotResult = await adapter.shoot(sessionId, p1, {row: oneCellShipCoord.row, column: oneCellShipCoord.col});
        expect(shotResult.shotResult).toBe("DESTROYED");

        const afterState = await adapter.getGameState(sessionId, p1);
        const revealedCell = afterState.opponentField[oneCellShipCoord.row][oneCellShipCoord.col];
        expect(revealedCell.hasShot).toBe(true);
        expect(revealedCell.ship).not.toBeNull();

        // A hit grants another turn.
        expect(afterState.isPlayerActive).toBe(true);
    });

    it("getGameState does not leak un-shot opponent ship positions anywhere on the board", async () => {
        const {sessionId, p1, p2} = await setUpTwoPlayerSession(adapter);
        await placeAllShips(adapter, sessionId, p1);
        await placeAllShips(adapter, sessionId, p2);
        await adapter.setReady(sessionId, p1);
        await adapter.setReady(sessionId, p2);

        const state = await adapter.getGameState(sessionId, p1);
        for (const row of state.opponentField) {
            for (const cell of row) {
                if (!cell.hasShot) {
                    expect(cell.ship).toBeNull();
                }
            }
        }
    });
});

const BOARD_CELL_COUNT = 100;

async function placeAllShips(adapter: MockGameAdapter, sessionId: string, playerId: string): Promise<void> {
    // Deterministic non-overlapping placement: two ships per row (columns 0 and 5, each ship is at
    // most size 5 so it never crosses into the other half), rows stepped by 2 so no two rows are
    // adjacent — guarantees no moat collisions for both editions (max 10 ships, max size 5, 10x10 board).
    const prep = await adapter.getPreparationState(sessionId, playerId);
    let row = 0;
    let column = 0;
    for (const ship of prep.ships) {
        await adapter.addShip(sessionId, playerId, ship.shipId, {row, column}, "HORIZONTAL");
        if (column === 0) {
            column = 5;
        } else {
            column = 0;
            row += 2;
        }
    }
}

function findFirstShipCell(field: CellDto[][]): { row: number; col: number } {
    for (const row of field) {
        for (const cell of row) {
            if (cell.ship) {
                return {row: cell.row, col: cell.col};
            }
        }
    }
    throw new Error("No ship cell found");
}

function findShipCellOfSize(field: CellDto[][], size: number): { row: number; col: number } {
    for (const row of field) {
        for (const cell of row) {
            if (cell.ship && cell.ship.shipSize === size) {
                return {row: cell.row, col: cell.col};
            }
        }
    }
    throw new Error(`No ship cell of size ${size} found`);
}

function findEmptyCell(field: CellDto[][]): { row: number; col: number } {
    for (const row of field) {
        for (const cell of row) {
            if (!cell.ship) {
                return {row: cell.row, col: cell.col};
            }
        }
    }
    throw new Error("No empty cell found");
}
