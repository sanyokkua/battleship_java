import axios, {AxiosRequestConfig} from "axios";
import axiosRetry from "axios-retry";
import {
    Coordinate,
    ParamCoordinateDto,
    ParamGameEditionDto,
    ParamPlayerNameDto,
    ParamShipDto,
    ResponseAvailableGameEditionsDto,
    ResponseCreatedPlayerDto,
    ResponseCreatedSessionIdDto,
    ResponseCurrentGameStageDto,
    ResponseGameplayStateDto,
    ResponseLastSessionChangeTimeDto,
    ResponseOpponentInformationDto,
    ResponsePlayerReady,
    ResponsePreparationState,
    ResponseShipAddedDto,
    ResponseShipRemovedDto,
    ResponseShotResultDto,
    ShipDirection
} from "../logic/ApplicationTypes";

// Retry idempotent-ish requests up to 3 times on network/5xx failures (applies to the shared
// axios instance, so it covers every call in this module).
axiosRetry(axios, {retries: 3});

/**
 * Guards path-variable inputs (session/player/ship ids) before they're interpolated into a
 * request URL. Throws a plain Error (not GameAdapterError) if the value is missing or too
 * short — callers (HttpGameAdapter) are expected to catch and translate as needed.
 */
function validateStringValue(value: string): void {
    if (!value || value.length < 2) {
        throw new Error("path variable is not valid");
    }
}

const BASE_API_URL = "/api/v2/game";

/**
 * Fetches the list of game editions (rule sets) the backend supports.
 */
export function getAvailableGameEditions(): Promise<ResponseAvailableGameEditionsDto> {
    const path = `${BASE_API_URL}/editions`;

    return axios.get<ResponseAvailableGameEditionsDto>(path).then(axiosResponse => axiosResponse.data);
}

/**
 * Creates a new game session for the given edition and returns its generated session id.
 */
export function createGameSession(gameEdition: string): Promise<ResponseCreatedSessionIdDto> {
    const path = `${BASE_API_URL}/sessions`;

    const data: ParamGameEditionDto = {
        gameEdition: gameEdition
    };

    return axios.post<ResponseCreatedSessionIdDto>(path, data).then(axiosResponse => axiosResponse.data);
}

/**
 * Registers a new player under the given session and returns the created player record
 * (including the generated player id).
 */
export function createPlayerInSession(sessionId: string, playerName: string): Promise<ResponseCreatedPlayerDto> {
    validateStringValue(sessionId);
    validateStringValue(playerName);
    const path = `${BASE_API_URL}/sessions/${sessionId}/players`;

    const data: ParamPlayerNameDto = {
        playerName: playerName
    };

    return axios.post<ResponseCreatedPlayerDto>(path, data).then(axiosResponse => axiosResponse.data);
}

/**
 * Fetches the session's current GameStage (used for polling/routing decisions).
 */
export function getCurrentGameStage(sessionId: string): Promise<ResponseCurrentGameStageDto> {
    validateStringValue(sessionId);
    const path = `${BASE_API_URL}/sessions/${sessionId}/state`;

    return axios.get<ResponseCurrentGameStageDto>(path).then(axiosResponse => axiosResponse.data);
}

/**
 * Fetches the timestamp of the session's last change, used by polling hooks to detect
 * updates without re-fetching full state.
 */
export function getLastSessionChangeTime(sessionId: string): Promise<ResponseLastSessionChangeTimeDto> {
    validateStringValue(sessionId);
    const path = `${BASE_API_URL}/sessions/${sessionId}/changesTime`;

    return axios.get<ResponseLastSessionChangeTimeDto>(path).then(axiosResponse => axiosResponse.data);
}

/**
 * Fetches the given player's current preparation (ship-placement) state.
 */
export function getPreparationState(sessionId: string, playerId: string): Promise<ResponsePreparationState> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `${BASE_API_URL}/sessions/${sessionId}/players/${playerId}/preparationState`;

    return axios.get<ResponsePreparationState>(path).then(axiosResponse => axiosResponse.data);
}

/**
 * Places the given ship on the player's field at the given coordinate/direction.
 */
export function addShipToField(sessionId: string, playerId: string, shipId: string,
                               coordinate: Coordinate, direction: ShipDirection): Promise<ResponseShipAddedDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    validateStringValue(shipId);
    const path = `${BASE_API_URL}/sessions/${sessionId}/players/${playerId}/ships/${shipId}`;

    const data: ParamShipDto = {
        row: coordinate.row,
        col: coordinate.column,
        direction: direction
    };

    return axios.put<ResponseShipAddedDto>(path, data).then(axiosResponse => axiosResponse.data);
}

/**
 * Removes whichever ship occupies the given coordinate on the player's field.
 */
export function removeShipFromField(sessionId: string, playerId: string, coordinate: Coordinate): Promise<ResponseShipRemovedDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `${BASE_API_URL}/sessions/${sessionId}/players/${playerId}/ships`;

    const data: ParamCoordinateDto = {
        row: coordinate.row,
        col: coordinate.column
    };
    const config: AxiosRequestConfig = {
        data: data
    };

    return axios.delete<ResponseShipRemovedDto>(path, config).then(axiosResponse => axiosResponse.data);
}

/**
 * Fetches what the given player is currently allowed to know about their opponent
 * (e.g. board state as revealed by shots taken so far).
 */
export function getOpponentInformation(sessionId: string, playerId: string): Promise<ResponseOpponentInformationDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `${BASE_API_URL}/sessions/${sessionId}/players/${playerId}/opponent`;

    return axios.get<ResponseOpponentInformationDto>(path).then(axiosResponse => axiosResponse.data);
}

/**
 * Marks the given player as ready/started, advancing the session once both players have
 * called this.
 */
export function startGame(sessionId: string, playerId: string): Promise<ResponsePlayerReady> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `${BASE_API_URL}/sessions/${sessionId}/players/${playerId}/start`;

    return axios.post<ResponsePlayerReady>(path, null).then(axiosResponse => axiosResponse.data);
}

/**
 * Fetches the full gameplay state (both fields, turn, stage) as visible to the given player.
 */
export function getGameStateForPlayer(sessionId: string, playerId: string): Promise<ResponseGameplayStateDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `${BASE_API_URL}/sessions/${sessionId}/players/${playerId}/state`;

    return axios.get<ResponseGameplayStateDto>(path).then(axiosResponse => axiosResponse.data);
}

/**
 * Fires a shot from the given player at the given coordinate on the opponent's field.
 */
export function makeShotByField(sessionId: string, playerId: string, coordinate: Coordinate): Promise<ResponseShotResultDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `${BASE_API_URL}/sessions/${sessionId}/players/${playerId}/field/shot`;

    const data: ParamCoordinateDto = {
        row: coordinate.row,
        col: coordinate.column
    };

    return axios.post<ResponseShotResultDto>(path, data).then(axiosResponse => axiosResponse.data);
}