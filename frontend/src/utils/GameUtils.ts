import {InitialData, ResponseCreatedPlayerDto} from "../logic/ApplicationTypes";
import * as promiseService2 from "../services/BackendRequestService";
import * as storage from "../services/GameBrowserStorage";

export async function createPlayerAsync(sessionId: string, playerName: string): Promise<ResponseCreatedPlayerDto> {
    if (!sessionId || sessionId.trim().length === 0 || !playerName || playerName.trim().length === 0) {
        throw new Error("Initial params are not valid!");
    }
    const playerDto = await promiseService2.createPlayerInSession(sessionId, playerName);
    if (!playerDto || !playerDto.playerId || playerDto.playerId.trim().length === 0) {
        throw new Error("Player is not created");
    }
    return playerDto;
}

export async function createSessionAsync(gameEdition: string): Promise<string> {
    const gameSessionDto = await promiseService2.createGameSession(gameEdition);
    if (!gameSessionDto || !gameSessionDto.sessionId || gameSessionDto.sessionId.length === 0) {
        throw new Error("Session is not created");
    }
    return gameSessionDto.sessionId;
}

export async function loadInitialDataAsync(): Promise<InitialData> {
    const sessionId = storage.loadSession();
    const player = storage.loadPlayer();
    const stage = storage.loadStage();

    let sessionIdToReturn = sessionId || null;
    let playerToReturn = player || null;
    let stageToReturn = stage || null;

    return {
        sessionId: sessionIdToReturn,
        player: playerToReturn,
        stage: stageToReturn
    };
}
