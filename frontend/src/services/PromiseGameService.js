import axios from "axios";

function validateStringPathVariable(value) {
    if (!value || typeof value !== "string" || value.length < 2) {
        throw new Error("path variable is not valid");
    }
}

function get(path) {
    if (!path) {
        throw new Error("params are incorrect");
    }
    return axios.get(path)
                .then(axiosResponse => axiosResponse.data)
                .catch(axiosError => console.log(axiosError));
}

function put(path, data) {
    if (!path) {
        throw new Error("params are incorrect");
    }
    return axios.put(path, data)
                .then(axiosResponse => axiosResponse.data)
                .catch(axiosError => console.log(axiosError));
}

function post(path, data) {
    if (!path) {
        throw new Error("params are incorrect");
    }
    return axios.post(path, data)
                .then(axiosResponse => axiosResponse.data)
                .catch(axiosError => console.log(axiosError));
}

function del(path, data) {
    if (!path) {
        throw new Error("params are incorrect");
    }
    return axios.delete(path, {data: data})
                .then(axiosResponse => axiosResponse.data)
                .catch(axiosError => console.log(axiosError));
}

export function getGameEditions() {
    const path = "/api/game/editions";
    return get(path);
}

export function createGameSession(gameEdition) {
    const path = "/api/game/sessions";
    const data = {
        gameEdition: gameEdition
    };
    return post(path, data);
}

export function createPlayerInSession(sessionId, playerName) {
    validateStringPathVariable(sessionId);
    const path = `/api/game/sessions/${sessionId}/players`;
    const data = {
        playerName: playerName
    };
    return post(path, data);
}

export function addShipToField(sessionId, playerId, shipId, coordinate, direction) {
    validateStringPathVariable(sessionId);
    validateStringPathVariable(playerId);
    validateStringPathVariable(shipId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/ships/${shipId}`;
    const data = {
        coordinate: {
            row: coordinate.row,
            column: coordinate.column
        },
        shipDirection: direction
    };
    return put(path, data);
}

export function removeShipFromField(sessionId, playerId, coordinate) {
    validateStringPathVariable(sessionId);
    validateStringPathVariable(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/ships?delete`;
    const data = {
        row: coordinate.row,
        column: coordinate.column
    };
    return del(path, data);
}

export function getPrepareShipsList(sessionId, playerId) {
    validateStringPathVariable(sessionId);
    validateStringPathVariable(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/ships?available`;
    return get(path);
}

export function startGame(sessionId, playerId) {
    validateStringPathVariable(sessionId);
    validateStringPathVariable(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}?start`;
    return post(path, null);
}

export function getPlayer(sessionId, playerId) {
    validateStringPathVariable(sessionId);
    validateStringPathVariable(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}`;
    return get(path);
}

export function getOpponent(sessionId, playerId) {
    validateStringPathVariable(sessionId);
    validateStringPathVariable(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}?opponent`;
    return get(path);
}

export function getField(sessionId, playerId) {
    validateStringPathVariable(sessionId);
    validateStringPathVariable(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/field`;
    return get(path);
}

export function getFieldOfOpponent(sessionId, playerId) {
    validateStringPathVariable(sessionId);
    validateStringPathVariable(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/field?opponent`;
    return get(path);
}

export function getActivePlayer(sessionId) {
    validateStringPathVariable(sessionId);
    const path = `/api/game/sessions/${sessionId}/players?active`;
    return get(path);
}

export function makeShot(sessionId, playerId, coordinate) {
    validateStringPathVariable(sessionId);
    validateStringPathVariable(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/field?shot`;
    const data = {
        row: coordinate.row,
        column: coordinate.column
    };
    return post(path, data);
}

export function getNumberOfUndamagedCells(sessionId, playerId) {
    validateStringPathVariable(sessionId);
    validateStringPathVariable(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/cells`;
    return get(path);
}

export function getNumberOfNotDestroyedShips(sessionId, playerId) {
    validateStringPathVariable(sessionId);
    validateStringPathVariable(playerId);
    const path = `/api/game/sessions/${sessionId}/players/${playerId}/ships?NotDestroyed`;
    return get(path);
}

export function getWinner(sessionId) {
    validateStringPathVariable(sessionId);
    const path = `/api/game/sessions/${sessionId}/winner`;
    return get(path);
}

export function getStage(sessionId) {
    validateStringPathVariable(sessionId);
    const path = `/api/game/sessions/${sessionId}/stage`;
    return get(path);
}