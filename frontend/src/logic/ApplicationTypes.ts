/**
 * Enumeration of the ship kinds available in the Battleship game.
 */
export type ShipType = "PATROL_BOAT" | "SUBMARINE" | "DESTROYER" | "BATTLESHIP" | "CARRIER";

/**
 * Enumeration of the orientations a ship can be placed with on the board.
 */
export type ShipDirection = "HORIZONTAL" | "VERTICAL";

/**
 * A single board position, expressed as a zero-based row/column pair.
 *
 * @property row - The row coordinate of the cell.
 * @property column - The column coordinate of the cell.
 */
export type Coordinate = {
    row: number,
    column: number
}

/**
 * Client-side view of a ship, as received from the backend.
 *
 * @property shipId - The unique identifier of the ship.
 * @property shipSize - The size of the ship in grid spaces.
 */
export type ShipDto = {
    shipId: string,
    shipSize: number,
}

/**
 * Client-side view of a single board cell, including its coordinates, associated ship,
 * shot status, and availability status.
 *
 * @property row - The row coordinate of the cell.
 * @property col - The column coordinate of the cell.
 * @property ship - The ship occupying the cell, or null if the cell is empty.
 * @property hasShot - Indicates whether the cell has been shot at.
 * @property isAvailable - Indicates whether the cell is available for placing a ship.
 */
export type CellDto = {
    row: number,
    col: number,
    ship: ShipDto | null,
    hasShot: boolean,
    isAvailable: boolean,
}

/**
 * Client-side view of the full gameplay state, including player and opponent details,
 * both fields, and winner information.
 *
 * @property playerName - The name of the player.
 * @property isPlayerActive - Indicates whether the player is currently active (it is their turn).
 * @property isPlayerWinner - Indicates whether the player is the winner.
 * @property playerNumberOfAliveCells - The number of alive cells for the player.
 * @property playerNumberOfAliveShips - The number of alive ships for the player.
 * @property playerField - The player's own field.
 * @property opponentName - The name of the opponent.
 * @property isOpponentReady - Indicates whether the opponent is ready.
 * @property opponentNumberOfAliveCells - The number of alive cells for the opponent.
 * @property opponentNumberOfAliveShips - The number of alive ships for the opponent.
 * @property opponentField - The opponent's field, as visible to the player (shots only).
 * @property hasWinner - Indicates whether there is a winner.
 * @property winnerPlayerName - The name of the winner player, if any.
 */
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

/**
 * Result of a single shot, as returned by the backend.
 *
 * @property shotResult - The result of the shot, represented as a string (e.g. hit or miss).
 */
export type ResponseShotResultDto = {
    shotResult: string,
}

/**
 * Request payload describing where and how to place a ship during preparation.
 *
 * @property row - The row coordinate for placing the ship.
 * @property col - The column coordinate for placing the ship.
 * @property direction - The direction in which to place the ship (e.g., HORIZONTAL, VERTICAL).
 */
export type ParamShipDto = {
    row: number,
    col: number,
    direction: string
}

/**
 * Client-side view of opponent information during the preparation stage.
 *
 * @property playerName - The name of the opponent player.
 * @property ready - Indicates whether the opponent player is ready.
 */
export type ResponseOpponentInformationDto = {
    playerName: string,
    ready: boolean,
}

/**
 * Client-side view of a player's readiness status.
 *
 * @property ready - Indicates whether the player is ready.
 */
export type ResponsePlayerReady = {
    ready: boolean,
}

/**
 * Client-side view of the preparation state, including the list of ships and the
 * current field status.
 *
 * @property ships - The list of ships in the preparation state.
 * @property field - The field status in the preparation state.
 */
export type ResponsePreparationState = {
    ships: ShipDto[],
    field: CellDto[][],
}

/**
 * Response confirming that a ship has been added during preparation.
 *
 * @property shipId - The unique identifier of the added ship.
 */
export type ResponseShipAddedDto = {
    shipId: string,
}

/**
 * Response confirming whether a ship has been removed during preparation.
 *
 * @property deleted - Indicates whether the ship has been deleted.
 */
export type ResponseShipRemovedDto = {
    deleted: boolean,
}

/**
 * Request payload identifying a single board coordinate (e.g. for firing a shot).
 *
 * @property row - The row coordinate of the cell.
 * @property col - The column coordinate of the cell.
 */
export type ParamCoordinateDto = {
    row: number,
    col: number
}

/**
 * Request payload identifying the chosen game edition.
 *
 * @property gameEdition - The name of the game edition.
 */
export type ParamGameEditionDto = {
    gameEdition: string,
}

/**
 * Request payload identifying a player's name.
 *
 * @property playerName - The name of the player.
 */
export type ParamPlayerNameDto = {
    playerName: string,
}

/**
 * Client-side view of the list of game editions available to choose from.
 *
 * @property gameEditions - The list of available game editions.
 */
export type ResponseAvailableGameEditionsDto = {
    gameEditions: string[],
}

/**
 * Client-side view of a newly created player, including their ID and name.
 *
 * @property playerId - The unique identifier of the player.
 * @property playerName - The name of the player.
 */
export type ResponseCreatedPlayerDto = {
    playerId: string,
    playerName: string,
}

/**
 * Client-side view of a newly created session's identifier.
 *
 * @property sessionId - The unique identifier of the created session.
 */
export type ResponseCreatedSessionIdDto = {
    sessionId: string,
}

/**
 * Client-side view of the current game stage.
 *
 * @property gameStage - The current game stage, represented as a string.
 */
export type ResponseCurrentGameStageDto = {
    gameStage: string,
}

/**
 * Client-side view of the last session change marker, used for polling.
 *
 * @property lastId - The ID of the last session change.
 */
export type ResponseLastSessionChangeTimeDto = {
    lastId: string,
}

/**
 * Client-side view of a Server-Sent Events push notification, sent immediately on
 * subscribe and again whenever the session's state changes.
 *
 * @property gameStage - The session's current GameStage, represented as a string.
 * @property lastUpdate - The time of the last session change.
 * @property opponent - The subscribing player's opponent info, or null until an opponent has joined.
 * @property gameplayState - Full gameplay state for the subscribing player, or null until the
 * session is IN_GAME or FINISHED.
 */
export type ResponseSessionPushDto = {
    gameStage: string,
    lastUpdate: string,
    opponent: ResponseOpponentInformationDto | null,
    gameplayState: ResponseGameplayStateDto | null,
}

/**
 * Bootstrap data loaded when the application starts, combining any previously
 * persisted session ID, player, and game stage.
 *
 * @property sessionId - The ID of the previously created/joined session, or null if none exists.
 * @property player - The current player, or null if no player has been created yet.
 * @property stage - The current game stage, or null if it could not be determined.
 */
export type InitialData = {
    sessionId: string | null,
    player: ResponseCreatedPlayerDto | null,
    stage: string | null,
}

/**
 * Client-side view of a backend error response.
 *
 * @property status - The HTTP status code of the exception.
 * @property errorMessage - The human-readable error message.
 * @property errorCode - The stable, machine-readable error code identifying the exception type, if provided.
 */
export type ExceptionDto = {
    status: number,
    errorMessage: string,
    errorCode?: string,
}
