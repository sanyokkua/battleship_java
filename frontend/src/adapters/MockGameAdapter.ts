import type {
    CellDto,
    Coordinate,
    ResponseCreatedPlayerDto,
    ResponseGameplayStateDto,
    ResponseOpponentInformationDto,
    ResponsePlayerReady,
    ResponsePreparationState,
    ResponseSessionPushDto,
    ResponseShipAddedDto,
    ResponseShipRemovedDto,
    ResponseShotResultDto,
    ShipDirection,
    ShipDto
} from "../logic/ApplicationTypes";
import type {GameAdapter} from "./GameAdapter";
import {GameAdapterError} from "./AdapterErrors";

const BOARD_SIZE = 10;

/** Ship-size makeup per edition, mirroring the backend's GameEditionConfiguration classes. */
const EDITION_SHIP_SIZES: Record<string, number[]> = {
    // Ukrainian: Patrol x4 (1), Submarine x3 (2), Destroyer x2 (3), Battleship x1 (4)
    UKRAINIAN: [1, 1, 1, 1, 2, 2, 2, 3, 3, 4],
    // Milton Bradley: Submarine x4 (2), Destroyer x3 (3), Battleship x2 (4), Carrier x1 (5)
    MILTON_BRADLEY: [2, 2, 2, 2, 3, 3, 3, 4, 4, 5]
};

type PlacedShip = {
    shipId: string;
    size: number;
    cells: Coordinate[];
};

type PlayerState = {
    playerId: string;
    playerName: string;
    ready: boolean;
    ships: ShipDto[]; // catalog of ships this player must place (id + size), matches edition makeup
    placedShips: Map<string, PlacedShip>; // shipId -> placement
    field: CellDto[][]; // 10x10, this player's own board (ships + shots against them)
};

type SessionState = {
    sessionId: string;
    edition: string;
    stage: string; // GameStage enum literal
    players: PlayerState[]; // in join order; players[0] created session (order also = default turn order)
    activePlayerId: string | null; // whose turn it is once IN_GAME
    hasWinner: boolean;
    winnerPlayerName: string | null;
    changeCounter: number; // bumped on every state-affecting mutation; exposed via getChangeTime
};

function emptyField(): CellDto[][] {
    const field: CellDto[][] = [];
    for (let r = 0; r < BOARD_SIZE; r++) {
        const row: CellDto[] = [];
        for (let c = 0; c < BOARD_SIZE; c++) {
            row.push({row: r, col: c, ship: null, hasShot: false, isAvailable: true});
        }
        field.push(row);
    }
    return field;
}

function inBounds(row: number, col: number): boolean {
    return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
}

function shipCells(at: Coordinate, size: number, dir: ShipDirection): Coordinate[] {
    const cells: Coordinate[] = [];
    for (let i = 0; i < size; i++) {
        const row = dir === "HORIZONTAL" ? at.row : at.row + i;
        const column = dir === "HORIZONTAL" ? at.column + i : at.column;
        cells.push({row, column});
    }
    return cells;
}

function neighbours(coord: Coordinate): Coordinate[] {
    const result: Coordinate[] = [];
    for (let dr = -1; dr <= 1; dr++) {
        for (let dc = -1; dc <= 1; dc++) {
            if (dr === 0 && dc === 0) continue;
            const row = coord.row + dr;
            const column = coord.column + dc;
            if (inBounds(row, column)) {
                result.push({row, column});
            }
        }
    }
    return result;
}

/** Recomputes CellDto.ship/isAvailable for every cell from the current set of placed ships, preserving hasShot. */
function rebuildFieldFromShips(field: CellDto[][], placedShips: Map<string, PlacedShip>): void {
    for (let r = 0; r < BOARD_SIZE; r++) {
        for (let c = 0; c < BOARD_SIZE; c++) {
            field[r][c].ship = null;
            field[r][c].isAvailable = true;
        }
    }

    for (const placed of placedShips.values()) {
        for (const cell of placed.cells) {
            field[cell.row][cell.column].ship = {shipId: placed.shipId, shipSize: placed.size};
            field[cell.row][cell.column].isAvailable = false;
        }
    }

    for (const placed of placedShips.values()) {
        for (const cell of placed.cells) {
            for (const n of neighbours(cell)) {
                if (!field[n.row][n.column].ship) {
                    field[n.row][n.column].isAvailable = false;
                }
            }
        }
    }
}

function cloneField(field: CellDto[][]): CellDto[][] {
    return field.map(row => row.map(cell => ({...cell, ship: cell.ship ? {...cell.ship} : null})));
}

function countAlive(field: CellDto[][]): { aliveCells: number; aliveShips: number } {
    let aliveCells = 0;
    const shipHits = new Map<string, number>(); // shipId -> hit count
    const shipSizes = new Map<string, number>();

    for (const row of field) {
        for (const cell of row) {
            if (cell.ship) {
                shipSizes.set(cell.ship.shipId, cell.ship.shipSize);
                if (!cell.hasShot) {
                    aliveCells++;
                } else {
                    shipHits.set(cell.ship.shipId, (shipHits.get(cell.ship.shipId) ?? 0) + 1);
                }
            }
        }
    }

    let aliveShips = 0;
    for (const [shipId, size] of shipSizes.entries()) {
        if ((shipHits.get(shipId) ?? 0) < size) {
            aliveShips++;
        }
    }

    return {aliveCells, aliveShips};
}

let idCounter = 0;

function nextId(prefix: string): string {
    idCounter += 1;
    return `${prefix}-${idCounter}`;
}

/**
 * Deterministic in-memory GameAdapter for tests / component development.
 * No network calls. Simplified relative to the real Java engine, but models
 * the same observable behavior: moat computation, ready-reset on ship
 * removal, shot resolution, and win detection.
 */
export class MockGameAdapter implements GameAdapter {
    private sessions = new Map<string, SessionState>();
    // sessionId -> playerId -> subscribed callbacks, simulating the backend's SSE fan-out.
    private listeners = new Map<string, Map<string, Set<(payload: ResponseSessionPushDto) => void>>>();

    /** See {@link GameAdapter.getEditions}. Returns the fixed list of editions this mock simulates. */
    async getEditions(): Promise<string[]> {
        return ["UKRAINIAN", "MILTON_BRADLEY"];
    }

    /** See {@link GameAdapter.createSession}. Throws `EDITION_INVALID` if `edition` isn't in {@link EDITION_SHIP_SIZES}. */
    async createSession(edition: string): Promise<string> {
        const sizes = EDITION_SHIP_SIZES[edition];
        if (!sizes) {
            throw new GameAdapterError(`Unknown edition: ${edition}`, {
                httpStatus: 400,
                errorCode: "EDITION_INVALID",
                context: "createSession"
            });
        }

        const sessionId = nextId("session");
        this.sessions.set(sessionId, {
            sessionId,
            edition,
            stage: "INITIALIZED",
            players: [],
            activePlayerId: null,
            hasWinner: false,
            winnerPlayerName: null,
            changeCounter: 0
        });
        return sessionId;
    }

    /**
     * See {@link GameAdapter.createPlayer}. Throws `STAGE_INVALID` once a
     * session already has two players; stocks the new player's ship catalog
     * from {@link EDITION_SHIP_SIZES} and advances the session to
     * WAITING_FOR_PLAYERS or PREPARATION depending on player count.
     */
    async createPlayer(sessionId: string, name: string): Promise<ResponseCreatedPlayerDto> {
        const session = this.requireSession(sessionId, "createPlayer");

        if (session.players.length >= 2) {
            throw new GameAdapterError("Session already has two players", {
                httpStatus: 400,
                errorCode: "STAGE_INVALID",
                context: "createPlayer"
            });
        }

        const playerId = nextId("player");
        const ships = (EDITION_SHIP_SIZES[session.edition] ?? []).map((size, index) => ({
            shipId: `${playerId}-ship-${index}`,
            shipSize: size
        }));

        const player: PlayerState = {
            playerId,
            playerName: name,
            ready: false,
            ships,
            placedShips: new Map(),
            field: emptyField()
        };
        session.players.push(player);

        session.stage = session.players.length >= 2 ? "PREPARATION" : "WAITING_FOR_PLAYERS";
        this.bump(session);

        return {playerId, playerName: name};
    }

    /** See {@link GameAdapter.getStage}. Throws `SESSION_NOT_FOUND` if the session doesn't exist. */
    async getStage(sessionId: string): Promise<string> {
        return this.requireSession(sessionId, "getStage").stage;
    }

    /** See {@link GameAdapter.getChangeTime}. Returns the session's internal `changeCounter`, bumped by every mutating call. */
    async getChangeTime(sessionId: string): Promise<string> {
        return String(this.requireSession(sessionId, "getChangeTime").changeCounter);
    }

    /** See {@link GameAdapter.getPreparationState}. Ship list excludes already-placed ships; field is a defensive clone. */
    async getPreparationState(sessionId: string, playerId: string): Promise<ResponsePreparationState> {
        const {player} = this.requirePlayer(sessionId, playerId, "getPreparationState");
        const remainingShips = player.ships.filter(s => !player.placedShips.has(s.shipId));
        return {ships: remainingShips, field: cloneField(player.field)};
    }

    /**
     * See {@link GameAdapter.addShip}. Validates the shipId belongs to the
     * player's catalog and isn't already placed (`SHIP_ID_INVALID`), and that
     * every resulting cell is in bounds and outside the moat of existing
     * ships (`COORDINATE_INVALID`), before recomputing the field via
     * {@link rebuildFieldFromShips}.
     */
    async addShip(sessionId: string, playerId: string, shipId: string, at: Coordinate, dir: ShipDirection): Promise<ResponseShipAddedDto> {
        const {session, player} = this.requirePlayer(sessionId, playerId, "addShip");

        const shipMeta = player.ships.find(s => s.shipId === shipId);
        if (!shipMeta) {
            throw new GameAdapterError(`Unknown shipId: ${shipId}`, {
                httpStatus: 400,
                errorCode: "SHIP_ID_INVALID",
                context: "addShip"
            });
        }
        if (player.placedShips.has(shipId)) {
            throw new GameAdapterError(`Ship already placed: ${shipId}`, {
                httpStatus: 400,
                errorCode: "SHIP_ID_INVALID",
                context: "addShip"
            });
        }

        const cells = shipCells(at, shipMeta.shipSize, dir);
        for (const cell of cells) {
            if (!inBounds(cell.row, cell.column)) {
                throw new GameAdapterError("Ship placement out of bounds", {
                    httpStatus: 400,
                    errorCode: "COORDINATE_INVALID",
                    context: "addShip"
                });
            }
            if (!player.field[cell.row][cell.column].isAvailable) {
                throw new GameAdapterError("Ship placement too close to another ship", {
                    httpStatus: 400,
                    errorCode: "COORDINATE_INVALID",
                    context: "addShip"
                });
            }
        }

        player.placedShips.set(shipId, {shipId, size: shipMeta.shipSize, cells});
        rebuildFieldFromShips(player.field, player.placedShips);
        this.bump(session);

        return {shipId};
    }

    /**
     * See {@link GameAdapter.removeShip}. Returns `{deleted: false}` (no
     * throw) if no ship occupies `at`; on success, un-readies the player,
     * mirroring the real engine's ready-reset behavior.
     */
    async removeShip(sessionId: string, playerId: string, at: Coordinate): Promise<ResponseShipRemovedDto> {
        const {session, player} = this.requirePlayer(sessionId, playerId, "removeShip");

        let foundShipId: string | null = null;
        for (const placed of player.placedShips.values()) {
            if (placed.cells.some(c => c.row === at.row && c.column === at.column)) {
                foundShipId = placed.shipId;
                break;
            }
        }

        if (!foundShipId) {
            return {deleted: false};
        }

        player.placedShips.delete(foundShipId);
        rebuildFieldFromShips(player.field, player.placedShips);
        player.ready = false; // mirrors real engine: removing a ship un-readies the player
        this.bump(session);

        return {deleted: true};
    }

    /** See {@link GameAdapter.getOpponent}. Returns an empty/not-ready placeholder if no second player has joined yet. */
    async getOpponent(sessionId: string, playerId: string): Promise<ResponseOpponentInformationDto> {
        const {opponent} = this.requirePlayerAndOpponent(sessionId, playerId, "getOpponent");
        if (!opponent) {
            return {playerName: "", ready: false};
        }
        return {playerName: opponent.playerName, ready: opponent.ready};
    }

    /**
     * See {@link GameAdapter.setReady}. Throws `STAGE_INVALID` unless every
     * catalog ship has been placed; transitions the session to IN_GAME (with
     * player 0 going first) once both players are ready.
     */
    async setReady(sessionId: string, playerId: string): Promise<ResponsePlayerReady> {
        const {session, player} = this.requirePlayer(sessionId, playerId, "setReady");

        const allPlaced = player.ships.every(s => player.placedShips.has(s.shipId));
        if (!allPlaced) {
            throw new GameAdapterError("Cannot ready up before all ships are placed", {
                httpStatus: 400,
                errorCode: "STAGE_INVALID",
                context: "setReady"
            });
        }

        player.ready = true;

        if (session.players.length === 2 && session.players.every(p => p.ready)) {
            session.stage = "IN_GAME";
            session.activePlayerId = session.players[0].playerId;
        }
        this.bump(session);

        return {ready: player.ready};
    }

    /**
     * See {@link GameAdapter.getGameState}. Throws `STAGE_INVALID` if no
     * opponent has joined; the opponent's field is filtered through
     * {@link opponentVisibleField} so unshot ship positions stay hidden
     * unless the game has finished.
     */
    async getGameState(sessionId: string, playerId: string): Promise<ResponseGameplayStateDto> {
        const {session, player, opponent} = this.requirePlayerAndOpponent(sessionId, playerId, "getGameState");
        if (!opponent) {
            throw new GameAdapterError("Opponent not present yet", {
                httpStatus: 400,
                errorCode: "STAGE_INVALID",
                context: "getGameState"
            });
        }

        const playerCounts = countAlive(player.field);
        const opponentCounts = countAlive(opponent.field);

        return {
            playerName: player.playerName,
            isPlayerActive: session.activePlayerId === player.playerId,
            isPlayerWinner: session.hasWinner && session.winnerPlayerName === player.playerName,
            playerNumberOfAliveCells: playerCounts.aliveCells,
            playerNumberOfAliveShips: playerCounts.aliveShips,
            playerField: cloneField(player.field),
            opponentName: opponent.playerName,
            isOpponentReady: opponent.ready,
            opponentNumberOfAliveCells: opponentCounts.aliveCells,
            opponentNumberOfAliveShips: opponentCounts.aliveShips,
            opponentField: this.opponentVisibleField(opponent.field, session.stage === "FINISHED"),
            hasWinner: session.hasWinner,
            winnerPlayerName: session.winnerPlayerName ?? ""
        };
    }

    /**
     * See {@link GameAdapter.shoot}. Validates opponent presence, IN_GAME
     * stage, turn ownership (`PLAYER_NOT_ACTIVE`), bounds, and that the cell
     * hasn't already been shot (`CELL_ALREADY_SHOT`). Turn passes to the
     * opponent only on a miss (a hit grants another shot); sets `hasWinner`
     * and stage FINISHED once the opponent's last ship is sunk.
     */
    async shoot(sessionId: string, playerId: string, at: Coordinate): Promise<ResponseShotResultDto> {
        const {session, player, opponent} = this.requirePlayerAndOpponent(sessionId, playerId, "shoot");
        if (!opponent) {
            throw new GameAdapterError("Opponent not present yet", {
                httpStatus: 400,
                errorCode: "STAGE_INVALID",
                context: "shoot"
            });
        }
        if (session.stage !== "IN_GAME") {
            throw new GameAdapterError("Game is not in progress", {
                httpStatus: 400,
                errorCode: "STAGE_INVALID",
                context: "shoot"
            });
        }
        if (session.activePlayerId !== player.playerId) {
            throw new GameAdapterError("Not your turn", {
                httpStatus: 400,
                errorCode: "PLAYER_NOT_ACTIVE",
                context: "shoot"
            });
        }
        if (!inBounds(at.row, at.column)) {
            throw new GameAdapterError("Shot out of bounds", {
                httpStatus: 400,
                errorCode: "COORDINATE_INVALID",
                context: "shoot"
            });
        }

        const targetCell = opponent.field[at.row][at.column];
        if (targetCell.hasShot) {
            throw new GameAdapterError("Cell has already been shot", {
                httpStatus: 400,
                errorCode: "CELL_ALREADY_SHOT",
                context: "shoot"
            });
        }
        targetCell.hasShot = true;

        let shotResult: "HIT" | "MISS" | "DESTROYED" = "MISS";
        if (targetCell.ship) {
            const placed = opponent.placedShips.get(targetCell.ship.shipId);
            const destroyed = placed !== undefined && placed.cells.every(c => opponent.field[c.row][c.column].hasShot);
            shotResult = destroyed ? "DESTROYED" : "HIT";

            // Mirrors the real engine's FieldManagementImpl.processDestroyedShip: once a ship is
            // fully sunk, its moat cells are auto-marked hasShot too (no ship can ever be adjacent
            // to another, so those cells are provably safe) — not additional real shots.
            if (destroyed && placed) {
                for (const cell of placed.cells) {
                    for (const n of neighbours(cell)) {
                        opponent.field[n.row][n.column].hasShot = true;
                    }
                }
            }
        }

        // Turn switches to the opponent only on a miss (classic Battleship: hits grant another turn).
        if (shotResult === "MISS") {
            session.activePlayerId = opponent.playerId;
        }

        const opponentCounts = countAlive(opponent.field);
        if (opponentCounts.aliveShips === 0) {
            session.hasWinner = true;
            session.winnerPlayerName = player.playerName;
            session.stage = "FINISHED";
        }

        this.bump(session);

        return {shotResult};
    }

    /**
     * See {@link GameAdapter.subscribeToSessionEvents}. Registers `onEvent` and invokes it
     * once immediately with the current snapshot (matching the real backend's
     * snapshot-on-subscribe behavior), then again every time any of this session's mutating
     * methods runs (via {@link bump}). A no-op subscribe (returning a no-op unsubscribe) if
     * the session/player don't exist yet, mirroring how a real `EventSource` wouldn't throw
     * synchronously for a bad URL either.
     */
    subscribeToSessionEvents(sessionId: string, playerId: string, onEvent: (payload: ResponseSessionPushDto) => void): () => void {
        const session = this.sessions.get(sessionId);
        const player = session?.players.find(p => p.playerId === playerId);
        if (!session || !player) {
            return () => {
            };
        }

        if (!this.listeners.has(sessionId)) {
            this.listeners.set(sessionId, new Map());
        }
        const sessionListeners = this.listeners.get(sessionId)!;
        if (!sessionListeners.has(playerId)) {
            sessionListeners.set(playerId, new Set());
        }
        const playerListeners = sessionListeners.get(playerId)!;
        playerListeners.add(onEvent);

        onEvent(this.buildPushPayload(session, player));

        return () => {
            playerListeners.delete(onEvent);
        };
    }

    // --- internal helpers ---

    private opponentVisibleField(opponentField: CellDto[][], revealAll: boolean): CellDto[][] {
        // Only reveal ship info for cells that have actually been shot at (or fully sunk ships,
        // which are already all-hasShot=true by construction) — never leak un-shot ship positions,
        // unless the game has finished (revealAll), in which case the full fleet is shown.
        return opponentField.map(row => row.map(cell => ({
            row: cell.row,
            col: cell.col,
            ship: (cell.hasShot || revealAll) ? (cell.ship ? {...cell.ship} : null) : null,
            hasShot: cell.hasShot,
            isAvailable: cell.isAvailable
        })));
    }

    private bump(session: SessionState): void {
        session.changeCounter += 1;
        this.notifySubscribers(session);
    }

    /** Computes and delivers a fresh push payload to every player currently subscribed to `session`. */
    private notifySubscribers(session: SessionState): void {
        const sessionListeners = this.listeners.get(session.sessionId);
        if (!sessionListeners) {
            return;
        }
        for (const player of session.players) {
            const playerListeners = sessionListeners.get(player.playerId);
            if (!playerListeners || playerListeners.size === 0) {
                continue;
            }
            const payload = this.buildPushPayload(session, player);
            for (const listener of playerListeners) {
                listener(payload);
            }
        }
    }

    /**
     * Builds the full-state push payload for `player`'s point of view: `opponent` once a second
     * player has joined, and `gameplayState` once that opponent exists and the session is
     * IN_GAME or FINISHED — mirroring the real backend's `SessionEventBroadcaster`.
     */
    private buildPushPayload(session: SessionState, player: PlayerState): ResponseSessionPushDto {
        const opponent = session.players.find(p => p.playerId !== player.playerId) ?? null;

        let opponentDto: ResponseOpponentInformationDto | null = null;
        let gameplayState: ResponseGameplayStateDto | null = null;

        if (opponent) {
            opponentDto = {playerName: opponent.playerName, ready: opponent.ready};

            if (session.stage === "IN_GAME" || session.stage === "FINISHED") {
                const playerCounts = countAlive(player.field);
                const opponentCounts = countAlive(opponent.field);
                gameplayState = {
                    playerName: player.playerName,
                    isPlayerActive: session.activePlayerId === player.playerId,
                    isPlayerWinner: session.hasWinner && session.winnerPlayerName === player.playerName,
                    playerNumberOfAliveCells: playerCounts.aliveCells,
                    playerNumberOfAliveShips: playerCounts.aliveShips,
                    playerField: cloneField(player.field),
                    opponentName: opponent.playerName,
                    isOpponentReady: opponent.ready,
                    opponentNumberOfAliveCells: opponentCounts.aliveCells,
                    opponentNumberOfAliveShips: opponentCounts.aliveShips,
                    opponentField: this.opponentVisibleField(opponent.field, session.stage === "FINISHED"),
                    hasWinner: session.hasWinner,
                    winnerPlayerName: session.winnerPlayerName ?? ""
                };
            }
        }

        return {
            gameStage: session.stage,
            lastUpdate: String(session.changeCounter),
            opponent: opponentDto,
            gameplayState
        };
    }

    private requireSession(sessionId: string, context: string): SessionState {
        const session = this.sessions.get(sessionId);
        if (!session) {
            throw new GameAdapterError(`Session not found: ${sessionId}`, {
                httpStatus: 404,
                errorCode: "SESSION_NOT_FOUND",
                context
            });
        }
        return session;
    }

    private requirePlayer(sessionId: string, playerId: string, context: string): {
        session: SessionState;
        player: PlayerState
    } {
        const session = this.requireSession(sessionId, context);
        const player = session.players.find(p => p.playerId === playerId);
        if (!player) {
            throw new GameAdapterError(`Player not found: ${playerId}`, {
                httpStatus: 400,
                errorCode: "PLAYER_ID_INVALID",
                context
            });
        }
        return {session, player};
    }

    private requirePlayerAndOpponent(sessionId: string, playerId: string, context: string): {
        session: SessionState;
        player: PlayerState;
        opponent: PlayerState | null
    } {
        const {session, player} = this.requirePlayer(sessionId, playerId, context);
        const opponent = session.players.find(p => p.playerId !== playerId) ?? null;
        return {session, player, opponent};
    }
}
