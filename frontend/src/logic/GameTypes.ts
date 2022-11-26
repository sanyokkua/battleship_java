export type GameEditionsDto = {
    gameEditions: string[]
}

export type GameSessionIdDto = {
    gameSessionId: string
}

export type ShipType = "PATROL_BOAT" | "SUBMARINE" | "DESTROYER" | "BATTLESHIP" | "CARRIER";

export type ShipDirection = "HORIZONTAL" | "VERTICAL";

export type ShipDto = {
    shipId: string,
    shipType: ShipType,
    shipDirection: ShipDirection,
    shipSize: number,
};

export type CellDto = {
    row: number,
    col: number,
    ship: ShipDto | null,
    hasShot: boolean,
    isAvailable: boolean,
}

export type PlayerDto = {
    playerId: string,
    playerName: string,
    field: CellDto[][],
    shipsNotOnTheField: ShipDto[],
    allPlayerShips: ShipDto[],
    isActive: boolean,
    isWinner: boolean,
    isReady: boolean
}

export type PlayerBaseInfoDto = {
    playerName: string,
    isActive: boolean,
    isWinner: boolean,
    isReady: boolean
}

export type ShotResult = "MISS" | "HIT" | "DESTROYED";

export type ShotResultDto = {
    shotResult: ShotResult;
}

export type UndamagedCellsDto = {
    numberOfUndamagedCells: number
}

export type NumberOfAliveShipsDto = {
    numberOfAliveShips: number;
}

export type RemovedShipDto = {
    removedShipId: string;
}

export type Coordinate = {
    row: number,
    column: number
}

export type AddShipToFieldBody = {
    coordinate: Coordinate;
    shipDirection: ShipDirection;
}
export type GameStage = "INITIALIZED" | "WAITING_FOR_PLAYERS" | "PREPARATION" | "IN_GAME" | "FINISHED";

export type GameStageDto = {
    gameStage: GameStage;
}

export type GamePlayState = {
    opponent: PlayerBaseInfoDto,
    activePlayer: PlayerBaseInfoDto,
    playerNumberOfAliveCells: number,
    opponentNumberOfAliveCells: number,
    playerNumberOfAliveShips: number,
    opponentNumberOfAliveShips: number,
    playerField: CellDto[][],
    opponentField: CellDto[][],
}

export type LastGameUpdateDto = {
    gameStage: GameStage;
    lastId: string;
}

export type GameplayStateDto = {
    playerName: string,
    opponentName: string,
    isPlayerActive: boolean,
    isOpponentReady: boolean,
    playerNumberOfAliveCells: number,
    playerNumberOfAliveShips: number,
    opponentNumberOfAliveCells: number,
    opponentNumberOfAliveShips: number,
    playerField: CellDto[][],
    opponentField: CellDto[][],
    hasWinner: boolean
}
