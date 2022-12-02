export type ShipType = "PATROL_BOAT" | "SUBMARINE" | "DESTROYER" | "BATTLESHIP" | "CARRIER";

export type ShipDirection = "HORIZONTAL" | "VERTICAL";

export type Coordinate = {
    row: number,
    column: number
}

export type ShipDto = {
    shipId: string,
    shipSize: number,
}

export type CellDto = {
    row: number,
    col: number,
    ship: ShipDto | null,
    hasShot: boolean,
    isAvailable: boolean,
}

export type ResponseGameplayStateDto = {
    playerName: string,
    isPlayerActive: boolean,
    isPlayerWinner: boolean,
    playerNumberOfAliveCells: number,
    playerNumberOfAliveShips: number,
    playerField: CellDto[][],
    opponentName: string,
    isOpponentReady: boolean,
    opponentNumberOfAliveCells: number,
    opponentNumberOfAliveShips: number,
    opponentField: CellDto[][],
    hasWinner: boolean,
    winnerPlayerName: string,
}

export type ResponseShotResultDto = {
    shotResult: string,
}

export type ParamShipDto = {
    row: number,
    col: number,
    direction: string
}

export type ResponseOpponentInformationDto = {
    playerName: string,
    ready: boolean,
}

export type ResponsePlayerReady = {
    ready: boolean,
}

export type ResponsePreparationState = {
    ships: ShipDto[],
    field: CellDto[][],
}

export type ResponseShipAddedDto = {
    shipId: string,
}

export type ResponseShipRemovedDto = {
    deleted: boolean,
}

export type ParamCoordinateDto = {
    row: number,
    col: number
}

export type ParamGameEditionDto = {
    gameEdition: string,
}

export type ParamPlayerNameDto = {
    playerName: string,
}

export type ResponseAvailableGameEditionsDto = {
    gameEditions: string[],
}

export type ResponseCreatedPlayerDto = {
    playerId: string,
    playerName: string,
}

export type ResponseCreatedSessionIdDto = {
    sessionId: string,
}

export type ResponseCurrentGameStageDto = {
    gameStage: string,
}

export type ResponseLastSessionChangeTimeDto = {
    lastId: string,
}

export type InitialData = {
    sessionId: string | null,
    player: ResponseCreatedPlayerDto | null,
    stage: string | null,
}
