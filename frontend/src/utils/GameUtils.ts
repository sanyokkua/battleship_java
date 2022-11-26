import {GameStage, PlayerDto, ShipDto} from "../logic/GameTypes";
import * as storage from "../services/GameStorage";
import * as promiseService from "../services/PromiseGameService";

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

export function shipComparator(ship1: ShipDto, ship2: ShipDto) {
    if (ship1.shipSize > ship2.shipSize) {
        return 1;
    }
    if (ship1.shipSize < ship2.shipSize) {
        return -1;
    }
    return 0;
}
