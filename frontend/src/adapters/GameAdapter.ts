import type {
    Coordinate,
    ResponseCreatedPlayerDto,
    ResponseGameplayStateDto,
    ResponseOpponentInformationDto,
    ResponsePlayerReady,
    ResponsePreparationState,
    ResponseShipAddedDto,
    ResponseShipRemovedDto,
    ResponseShotResultDto,
    ShipDirection
} from "../logic/ApplicationTypes";

/**
 * The single port through which the frontend talks to the backend.
 *
 * No widget/page/hook may call the network directly — everything goes through
 * an implementation of this interface (HttpGameAdapter for real use,
 * MockGameAdapter for tests/dev), injected via GameAdapterContext.
 */
export interface GameAdapter {
    getEditions(): Promise<string[]>;

    createSession(edition: string): Promise<string>; // sessionId

    createPlayer(sessionId: string, name: string): Promise<ResponseCreatedPlayerDto>;

    getStage(sessionId: string): Promise<string>; // raw GameStage enum name

    getChangeTime(sessionId: string): Promise<string>; // lastId

    getPreparationState(sessionId: string, playerId: string): Promise<ResponsePreparationState>;

    addShip(sessionId: string, playerId: string, shipId: string, at: Coordinate, dir: ShipDirection): Promise<ResponseShipAddedDto>;

    removeShip(sessionId: string, playerId: string, at: Coordinate): Promise<ResponseShipRemovedDto>;

    getOpponent(sessionId: string, playerId: string): Promise<ResponseOpponentInformationDto>;

    setReady(sessionId: string, playerId: string): Promise<ResponsePlayerReady>;

    getGameState(sessionId: string, playerId: string): Promise<ResponseGameplayStateDto>;

    shoot(sessionId: string, playerId: string, at: Coordinate): Promise<ResponseShotResultDto>; // shotResult is "HIT"|"MISS"|"DESTROYED"
}
