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
    /**
     * Lists the game editions the backend currently supports (e.g. "UKRAINIAN", "MILTON_BRADLEY").
     * Used to populate the edition picker on the new-game screen.
     *
     * @returns The available edition identifiers.
     */
    getEditions(): Promise<string[]>;

    /**
     * Creates a new game session for the given edition.
     *
     * @param edition - One of the identifiers returned by {@link getEditions}.
     * @returns The new session's id.
     */
    createSession(edition: string): Promise<string>; // sessionId

    /**
     * Registers a new player in an existing session. The first player to join
     * a session starts it; the second moves it into preparation.
     *
     * @param sessionId - The session to join.
     * @param name - The player's display name.
     * @returns The created player's id and name.
     */
    createPlayer(sessionId: string, name: string): Promise<ResponseCreatedPlayerDto>;

    /**
     * Fetches the session's current lifecycle stage.
     *
     * @param sessionId - The session to query.
     * @returns The raw `GameStage` enum name (e.g. "PREPARATION", "IN_GAME").
     */
    getStage(sessionId: string): Promise<string>; // raw GameStage enum name

    /**
     * Fetches a monotonically increasing marker of the session's last state
     * change, used by polling hooks to detect whether a re-fetch is needed
     * without pulling the full state each time.
     *
     * @param sessionId - The session to query.
     * @returns An opaque, comparable "last changed" token.
     */
    getChangeTime(sessionId: string): Promise<string>; // lastId

    /**
     * Fetches a player's preparation-stage view: their remaining (unplaced)
     * ships and their own board.
     *
     * @param sessionId - The session to query.
     * @param playerId - The player whose preparation state is requested.
     * @returns The player's remaining ships and current field.
     */
    getPreparationState(sessionId: string, playerId: string): Promise<ResponsePreparationState>;

    /**
     * Places one of the player's ships on their board.
     *
     * @param sessionId - The session to mutate.
     * @param playerId - The player placing the ship.
     * @param shipId - Identifies which of the player's catalog ships to place.
     * @param at - The bow/origin coordinate of the ship.
     * @param dir - The ship's orientation from `at`.
     * @returns Confirmation of the placed ship's id.
     */
    addShip(sessionId: string, playerId: string, shipId: string, at: Coordinate, dir: ShipDirection): Promise<ResponseShipAddedDto>;

    /**
     * Removes whichever ship occupies the given cell, if any. Un-readies the
     * player if they had already called {@link setReady}.
     *
     * @param sessionId - The session to mutate.
     * @param playerId - The player removing a ship.
     * @param at - Any coordinate occupied by the ship to remove.
     * @returns Whether a ship was actually found and removed.
     */
    removeShip(sessionId: string, playerId: string, at: Coordinate): Promise<ResponseShipRemovedDto>;

    /**
     * Fetches the opponent's public preparation-stage info (name and
     * ready state) without exposing their ship placements.
     *
     * @param sessionId - The session to query.
     * @param playerId - The requesting player (used to identify "the other one").
     * @returns The opponent's name and readiness.
     */
    getOpponent(sessionId: string, playerId: string): Promise<ResponseOpponentInformationDto>;

    /**
     * Marks the player as ready to start. Requires all of the player's ships
     * to be placed first; once both players are ready the session transitions
     * to IN_GAME.
     *
     * @param sessionId - The session to mutate.
     * @param playerId - The player readying up.
     * @returns The player's resulting ready state.
     */
    setReady(sessionId: string, playerId: string): Promise<ResponsePlayerReady>;

    /**
     * Fetches the full gameplay-stage snapshot for a player: both boards
     * (their own field, and the opponent's field with unrevealed ships
     * hidden), whose turn it is, and win state.
     *
     * @param sessionId - The session to query.
     * @param playerId - The player requesting their view of the game.
     * @returns The gameplay state for this player.
     */
    getGameState(sessionId: string, playerId: string): Promise<ResponseGameplayStateDto>;

    /**
     * Fires a shot at a coordinate on the opponent's board. Only valid when
     * it is the calling player's turn.
     *
     * @param sessionId - The session to mutate.
     * @param playerId - The player taking the shot.
     * @param at - The targeted coordinate on the opponent's board.
     * @returns The shot outcome.
     */
    shoot(sessionId: string, playerId: string, at: Coordinate): Promise<ResponseShotResultDto>; // shotResult is "HIT"|"MISS"|"DESTROYED"
}
