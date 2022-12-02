import axios, {AxiosRequestConfig} from "axios";
import axiosRetry from "axios-retry";
import {
    Coordinate,
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
} from "../logic/RequestTypes";

axiosRetry(axios, {retries: 3});

function validateStringValue(value: string): void {
    if (!value || value.length < 2) {
        throw new Error("path variable is not valid");
    }
}

const BASE_API_URL = "/api/v2/game/";

export function getAvailableGameEditions(): Promise<ResponseAvailableGameEditionsDto> {
    const path = `${BASE_API_URL}/editions`;

    return axios.get<ResponseAvailableGameEditionsDto>(path).then(axiosResponse => axiosResponse.data);
}

export function createGameSession(gameEdition: string): Promise<ResponseCreatedSessionIdDto> {
    const path = `${BASE_API_URL}/sessions`;

    const data = {
        gameEdition: gameEdition
    };

    return axios.post<ResponseCreatedSessionIdDto>(path, data).then(axiosResponse => axiosResponse.data);
}

export function createPlayerInSession(sessionId: string, playerName: string): Promise<ResponseCreatedPlayerDto> {
    validateStringValue(sessionId);
    validateStringValue(playerName);
    const path = `${BASE_API_URL}/sessions/${sessionId}/players`;

    const data: ParamPlayerNameDto = {
        playerName: playerName
    };

    return axios.post<ResponseCreatedPlayerDto>(path, data).then(axiosResponse => axiosResponse.data);
}

export function getCurrentGameStage(sessionId: string): Promise<ResponseCurrentGameStageDto> {
    validateStringValue(sessionId);
    const path = `${BASE_API_URL}/sessions/${sessionId}/state`;

    return axios.get<ResponseCurrentGameStageDto>(path).then(axiosResponse => axiosResponse.data);
}

export function getLastSessionChangeTime(sessionId: string): Promise<ResponseLastSessionChangeTimeDto> {
    validateStringValue(sessionId);
    const path = `${BASE_API_URL}/sessions/${sessionId}/changesTime`;

    return axios.get<ResponseLastSessionChangeTimeDto>(path).then(axiosResponse => axiosResponse.data);
}

export function getPreparationState(sessionId: string, playerId: string): Promise<ResponsePreparationState> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `${BASE_API_URL}/sessions/${sessionId}/players/${playerId}/state`;

    return axios.get<ResponsePreparationState>(path).then(axiosResponse => axiosResponse.data);
}

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

export type ParamCoordinateDto = {
    row: number,
    col: number
}

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

export function getOpponentInformation(sessionId: string, playerId: string): Promise<ResponseOpponentInformationDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `${BASE_API_URL}/sessions/${sessionId}/players/${playerId}/opponent`;

    return axios.get<ResponseOpponentInformationDto>(path).then(axiosResponse => axiosResponse.data);
}

export function startGame(sessionId: string, playerId: string): Promise<ResponsePlayerReady> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `${BASE_API_URL}/sessions/${sessionId}/players/${playerId}/start`;

    return axios.post<ResponsePlayerReady>(path, null).then(axiosResponse => axiosResponse.data);
}

export function getGameStateForPlayer(sessionId: string, playerId: string): Promise<ResponseGameplayStateDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `${BASE_API_URL}/sessions/${sessionId}/players/${playerId}/state`;

    return axios.get<ResponseGameplayStateDto>(path).then(axiosResponse => axiosResponse.data);
}

export function makeShotByField(sessionId: string, playerId: string, coordinate: Coordinate): Promise<ResponseShotResultDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `${BASE_API_URL}/sessions/${sessionId}/players/${playerId}/field?shot`;

    const data: ParamCoordinateDto = {
        row: coordinate.row,
        col: coordinate.column
    };

    return axios.post<ResponseShotResultDto>(path, data).then(axiosResponse => axiosResponse.data);
}