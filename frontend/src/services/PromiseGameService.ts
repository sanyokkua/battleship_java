import axios, {AxiosRequestConfig} from "axios";
import {
    CellDto,
    Coordinate,
    GameEditionsDto,
    GameplayStateDto,
    GameSessionIdDto,
    GameStageDto,
    LastGameUpdateDto,
    NumberOfAliveShipsDto,
    PlayerBaseInfoDto,
    PlayerDto,
    RemovedShipDto,
    ShipDirection,
    ShipDto,
    ShotResultDto,
    UndamagedCellsDto
} from "../logic/GameTypes";
import axiosRetry from "axios-retry";

axiosRetry(axios, {retries: 3});

function validateStringValue(value: string): void {
    if (!value || value.length < 2) {
        throw new Error("path variable is not valid");
    }
}

export function getGameEditions(): Promise<GameEditionsDto> {
    const path = "/api/game/editions";
    return axios.get<GameEditionsDto>(path).then(axiosResponse => axiosResponse.data);
}

export function createGameSession(gameEdition: string): Promise<GameSessionIdDto> {
    const path = "/api/game/sessions";
    const data = {
        gameEdition: gameEdition
    };
    return axios.post<GameSessionIdDto>(path, data)
        .then(axiosResponse => axiosResponse.data);
}

export function createPlayerInSession(sessionId: string, playerName: string): Promise<PlayerDto> {
    validateStringValue(sessionId);
    const path = `/api/game/sessions/${sessionId}/players`;
    const data = {
        playerName: playerName
    };
    return axios.post<PlayerDto>(path, data).then(axiosResponse => axiosResponse.data);
}

export function addShipToField(sessionId: string, playerId: string, shipId: string,
                               coordinate: Coordinate, direction: ShipDirection): Promise<ShipDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    validateStringValue(shipId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/ships/${shipId}`;
    const data = {
        coordinate: {
            row: coordinate.row,
            column: coordinate.column
        },
        shipDirection: direction
    };
    return axios.put<ShipDto>(path, data).then(axiosResponse => axiosResponse.data);
}

export function removeShipFromField(sessionId: string, playerId: string, coordinate: Coordinate): Promise<RemovedShipDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/ships?delete`;
    const data = {
        row: coordinate.row,
        column: coordinate.column
    };
    const config: AxiosRequestConfig = {
        data: data
    };
    return axios.delete<RemovedShipDto>(path, config).then(axiosResponse => axiosResponse.data);
}

export function getPrepareShipsList(sessionId: string, playerId: string): Promise<ShipDto[]> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/ships?available`;
    return axios.get<ShipDto[]>(path).then(axiosResponse => axiosResponse.data);
}

export function startGame(sessionId: string, playerId: string): Promise<PlayerDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}?start`;
    return axios.post<PlayerDto>(path, null).then(axiosResponse => axiosResponse.data);
}

export function getPlayer(sessionId: string, playerId: string): Promise<PlayerDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}`;
    return axios.get<PlayerDto>(path).then(axiosResponse => axiosResponse.data);
}

export function getOpponent(sessionId: string, playerId: string): Promise<PlayerBaseInfoDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}?opponent`;
    return axios.get<PlayerBaseInfoDto>(path).then(axiosResponse => axiosResponse.data);
}

export function getField(sessionId: string, playerId: string): Promise<CellDto[][]> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/field`;
    return axios.get<CellDto[][]>(path).then(axiosResponse => axiosResponse.data);
}

export function getFieldOfOpponent(sessionId: string, playerId: string): Promise<CellDto[][]> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/field?opponent`;
    return axios.get<CellDto[][]>(path).then(axiosResponse => axiosResponse.data);
}

export function getActivePlayer(sessionId: string): Promise<PlayerBaseInfoDto> {
    validateStringValue(sessionId);
    const path = `/api/game/sessions/${sessionId}/players?active`;
    return axios.get<PlayerBaseInfoDto>(path).then(axiosResponse => axiosResponse.data);
}

export function makeShot(sessionId: string, playerId: string, coordinate: Coordinate): Promise<ShotResultDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/field?shot`;
    const data = {
        row: coordinate.row,
        column: coordinate.column
    };
    return axios.post<ShotResultDto>(path, data).then(axiosResponse => axiosResponse.data);
}

export function getNumberOfUndamagedCells(sessionId: string, playerId: string): Promise<UndamagedCellsDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/cells`;
    return axios.get<UndamagedCellsDto>(path).then(axiosResponse => axiosResponse.data);
}

export function getNumberOfNotDestroyedShips(sessionId: string, playerId: string): Promise<NumberOfAliveShipsDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/ships?NotDestroyed`;
    return axios.get<NumberOfAliveShipsDto>(path).then(axiosResponse => axiosResponse.data);
}

export function getNumberOfUndamagedCellsOpponent(sessionId: string, playerId: string): Promise<UndamagedCellsDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/opponent/cells`;
    return axios.get<UndamagedCellsDto>(path).then(axiosResponse => axiosResponse.data);
}

export function getNumberOfNotDestroyedShipsOpponent(sessionId: string, playerId: string): Promise<NumberOfAliveShipsDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/opponent/ships?NotDestroyed`;
    return axios.get<NumberOfAliveShipsDto>(path).then(axiosResponse => axiosResponse.data);
}

export function getWinner(sessionId: string): Promise<PlayerBaseInfoDto> {
    validateStringValue(sessionId);
    const path = `/api/game/sessions/${sessionId}/winner`;
    return axios.get<PlayerBaseInfoDto>(path).then(axiosResponse => axiosResponse.data);
}

export function getStage(sessionId: string): Promise<GameStageDto> {
    validateStringValue(sessionId);
    const path = `/api/game/sessions/${sessionId}/stage`;
    return axios.get<GameStageDto>(path).then(axiosResponse => axiosResponse.data);
}

export function getGameplayState(sessionId: string, playerId: string): Promise<GameplayStateDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/state`;
    return axios.get<GameplayStateDto>(path).then(axiosResponse => axiosResponse.data);
}

export function getLastUpdate(sessionId: string, playerId: string): Promise<LastGameUpdateDto> {
    validateStringValue(sessionId);
    validateStringValue(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/lastupdate`;
    return axios.get<LastGameUpdateDto>(path).then(axiosResponse => axiosResponse.data);
}