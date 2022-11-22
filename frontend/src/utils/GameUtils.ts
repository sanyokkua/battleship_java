import * as storage from "../services/GameStorage";
import * as promiseService from "../services/PromiseGameService";
import {
    CellDto,
    GamePlayState,
    GameStage,
    NumberOfAliveShipsDto,
    PlayerBaseInfoDto,
    PlayerDto,
    ShipDto,
    UndamagedCellsDto
} from "../logic/GameTypes";

export async function createPlayerAsync(sessionId: string, playerName: string): Promise<PlayerDto> {
    if (!sessionId || sessionId.trim().length === 0 || !playerName || playerName.trim().length === 0) {
        throw new Error("Initial params are not valid!");
    }
    const playerDto = await promiseService.createPlayerInSession(sessionId, playerName);
    if (!playerDto || !playerDto.playerId || playerDto.playerId.trim().length === 0) {
        throw new Error("Player is not created");
    }
    return playerDto;
}

export async function createSessionAsync(gameEdition: string): Promise<string> {
    const gameSessionDto = await promiseService.createGameSession(gameEdition);
    if (!gameSessionDto || !gameSessionDto.gameSessionId || gameSessionDto.gameSessionId.length === 0) {
        throw new Error("Session is not created");
    }
    return gameSessionDto.gameSessionId;
}

export async function getGameEditionsAsync(): Promise<string[]> {
    const gameEditions = await promiseService.getGameEditions();
    if (!gameEditions || !gameEditions.gameEditions || gameEditions.gameEditions.length === 0) {
        throw new Error("Game Editions are not loaded");
    }
    return gameEditions.gameEditions;
}

export type InitialData = {
    sessionId: string | null,
    player: PlayerDto | null,
    stage: GameStage | null,
    gameEditions: string[]
}

export async function loadInitialDataAsync(): Promise<InitialData> {
    try {
        const sessionId = storage.loadSession();
        const player = storage.loadPlayer();
        const stage = storage.loadStage();
        const gameEditionsToReturn = await promiseService.getGameEditions();

        let sessionIdToReturn = sessionId || null;
        let playerToReturn = player || null;
        let stageToReturn = stage || null;

        return {
            sessionId: sessionIdToReturn,
            player: playerToReturn,
            stage: stageToReturn,
            gameEditions: gameEditionsToReturn.gameEditions
        };
    } catch (e) {
        throw e;
    }
}

export async function loadGameplayData(sessionId: string, playerId: string): Promise<GamePlayState> {
    const opponentDto: PlayerBaseInfoDto = await promiseService.getOpponent(sessionId, playerId);
    const activePlayerDto: PlayerBaseInfoDto = await promiseService.getActivePlayer(sessionId);
    const aliveShipsDto: NumberOfAliveShipsDto = await promiseService.getNumberOfNotDestroyedShips(sessionId, playerId);
    const aliveCellsDto: UndamagedCellsDto = await promiseService.getNumberOfUndamagedCells(sessionId, playerId);
    const opponentAliveShipsDto: NumberOfAliveShipsDto = await promiseService.getNumberOfNotDestroyedShipsOpponent(sessionId, playerId);
    const opponentAliveCellsDto: UndamagedCellsDto = await promiseService.getNumberOfUndamagedCellsOpponent(sessionId, playerId);
    const playerField: CellDto[][] = await promiseService.getField(sessionId, playerId);
    const opponentField: CellDto[][] = await promiseService.getFieldOfOpponent(sessionId, playerId);

    const state: GamePlayState = {
        opponent: opponentDto,
        activePlayer: activePlayerDto,
        playerNumberOfAliveCells: aliveCellsDto.numberOfUndamagedCells,
        playerNumberOfAliveShips: aliveShipsDto.numberOfAliveShips,
        opponentNumberOfAliveCells: opponentAliveCellsDto.numberOfUndamagedCells,
        opponentNumberOfAliveShips: opponentAliveShipsDto.numberOfAliveShips,
        playerField: playerField,
        opponentField: opponentField
    };
    return state;
}

export function shipComparator(ship1: ShipDto, ship2: ShipDto) {
    if (ship1.shipSize > ship2.shipSize) {
        return 1;
    }
    if (ship1.shipSize < ship2.shipSize) {
        return -1;
    }
    return 0;
}