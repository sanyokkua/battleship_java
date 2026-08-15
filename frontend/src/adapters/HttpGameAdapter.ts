import type {AxiosError} from "axios";
import {
    addShipToField,
    createGameSession,
    createPlayerInSession,
    getAvailableGameEditions,
    getCurrentGameStage,
    getGameStateForPlayer,
    getLastSessionChangeTime,
    getOpponentInformation,
    getPreparationState,
    makeShotByField,
    removeShipFromField,
    startGame
} from "../services/BackendRequestService";
import type {
    Coordinate,
    ExceptionDto,
    ResponseCreatedPlayerDto,
    ResponseGameplayStateDto,
    ResponseOpponentInformationDto,
    ResponsePlayerReady,
    ResponsePreparationState,
    ResponseSessionPushDto,
    ResponseShipAddedDto,
    ResponseShipRemovedDto,
    ResponseShotResultDto,
    ShipDirection
} from "../logic/ApplicationTypes";
import type {GameAdapter} from "./GameAdapter";
import {GameAdapterError} from "./AdapterErrors";

/**
 * Production GameAdapter implementation. Delegates every operation to the
 * existing axios-based functions in services/BackendRequestService.ts and
 * translates any rejection into a GameAdapterError.
 */
export class HttpGameAdapter implements GameAdapter {
    /** Maps an axios rejection into a {@link GameAdapterError}, pulling httpStatus/errorCode/message out of the backend's `ExceptionDto` body when present. */
    private static toAdapterError(error: unknown, context: string): GameAdapterError {
        const axiosError = error as AxiosError<ExceptionDto>;
        const httpStatus = axiosError?.response?.status;
        const errorCode = axiosError?.response?.data?.errorCode;
        const message = axiosError?.response?.data?.errorMessage ?? axiosError?.message ?? "Unknown error";

        return new GameAdapterError(message, {
            httpStatus,
            errorCode,
            context,
            cause: error
        });
    }

    /** See {@link GameAdapter.getEditions}. Delegates to `getAvailableGameEditions`. */
    async getEditions(): Promise<string[]> {
        return this.wrap("getEditions()", async () => {
            const dto = await getAvailableGameEditions();
            return dto.gameEditions;
        });
    }

    /** See {@link GameAdapter.createSession}. Delegates to `createGameSession`. */
    async createSession(edition: string): Promise<string> {
        return this.wrap(`createSession(edition=${edition})`, async () => {
            const dto = await createGameSession(edition);
            return dto.sessionId;
        });
    }

    /** See {@link GameAdapter.createPlayer}. Delegates to `createPlayerInSession`. */
    async createPlayer(sessionId: string, name: string): Promise<ResponseCreatedPlayerDto> {
        return this.wrap(`createPlayer(sessionId=${sessionId}, name=${name})`, () =>
            createPlayerInSession(sessionId, name));
    }

    /** See {@link GameAdapter.getStage}. Delegates to `getCurrentGameStage`. */
    async getStage(sessionId: string): Promise<string> {
        return this.wrap(`getStage(sessionId=${sessionId})`, async () => {
            const dto = await getCurrentGameStage(sessionId);
            return dto.gameStage;
        });
    }

    /** See {@link GameAdapter.getChangeTime}. Delegates to `getLastSessionChangeTime`. */
    async getChangeTime(sessionId: string): Promise<string> {
        return this.wrap(`getChangeTime(sessionId=${sessionId})`, async () => {
            const dto = await getLastSessionChangeTime(sessionId);
            return dto.lastId;
        });
    }

    /** See {@link GameAdapter.getPreparationState}. Delegates to `getPreparationState`. */
    async getPreparationState(sessionId: string, playerId: string): Promise<ResponsePreparationState> {
        return this.wrap(`getPreparationState(sessionId=${sessionId}, playerId=${playerId})`, () =>
            getPreparationState(sessionId, playerId));
    }

    /** See {@link GameAdapter.addShip}. Delegates to `addShipToField`. */
    async addShip(sessionId: string, playerId: string, shipId: string, at: Coordinate, dir: ShipDirection): Promise<ResponseShipAddedDto> {
        return this.wrap(`addShip(sessionId=${sessionId}, playerId=${playerId}, shipId=${shipId}, at=${JSON.stringify(at)}, dir=${dir})`, () =>
            addShipToField(sessionId, playerId, shipId, at, dir));
    }

    /** See {@link GameAdapter.removeShip}. Delegates to `removeShipFromField`. */
    async removeShip(sessionId: string, playerId: string, at: Coordinate): Promise<ResponseShipRemovedDto> {
        return this.wrap(`removeShip(sessionId=${sessionId}, playerId=${playerId}, at=${JSON.stringify(at)})`, () =>
            removeShipFromField(sessionId, playerId, at));
    }

    /** See {@link GameAdapter.getOpponent}. Delegates to `getOpponentInformation`. */
    async getOpponent(sessionId: string, playerId: string): Promise<ResponseOpponentInformationDto> {
        return this.wrap(`getOpponent(sessionId=${sessionId}, playerId=${playerId})`, () =>
            getOpponentInformation(sessionId, playerId));
    }

    /** See {@link GameAdapter.setReady}. Delegates to `startGame` (the backend endpoint that marks a player ready). */
    async setReady(sessionId: string, playerId: string): Promise<ResponsePlayerReady> {
        return this.wrap(`setReady(sessionId=${sessionId}, playerId=${playerId})`, () =>
            startGame(sessionId, playerId));
    }

    /** See {@link GameAdapter.getGameState}. Delegates to `getGameStateForPlayer`. */
    async getGameState(sessionId: string, playerId: string): Promise<ResponseGameplayStateDto> {
        return this.wrap(`getGameState(sessionId=${sessionId}, playerId=${playerId})`, () =>
            getGameStateForPlayer(sessionId, playerId));
    }

    /** See {@link GameAdapter.shoot}. Delegates to `makeShotByField`. */
    async shoot(sessionId: string, playerId: string, at: Coordinate): Promise<ResponseShotResultDto> {
        return this.wrap(`shoot(sessionId=${sessionId}, playerId=${playerId}, at=${JSON.stringify(at)})`, () =>
            makeShotByField(sessionId, playerId, at));
    }

    /**
     * See {@link GameAdapter.subscribeToSessionEvents}. Opens a native `EventSource` against
     * the backend's SSE endpoint — same-origin, so no auth headers are needed (session/player
     * id already travel via the URL path, matching every other call in this adapter). Relies
     * on `EventSource`'s built-in auto-reconnect rather than custom backoff logic.
     */
    subscribeToSessionEvents(sessionId: string, playerId: string, onEvent: (payload: ResponseSessionPushDto) => void): () => void {
        const path = `/api/v2/game/sessions/${sessionId}/players/${playerId}/events`;
        const eventSource = new EventSource(path);

        eventSource.addEventListener("state-changed", (event: MessageEvent<string>) => {
            onEvent(JSON.parse(event.data) as ResponseSessionPushDto);
        });

        eventSource.addEventListener("error", (event) => {
            console.error(`SSE connection error for session ${sessionId} player ${playerId}`, event);
        });

        return () => {
            eventSource.close();
        };
    }

    /** Runs `fn`, converting any thrown/rejected error into a {@link GameAdapterError} tagged with `context` for diagnostics. */
    private async wrap<T>(context: string, fn: () => Promise<T>): Promise<T> {
        try {
            return await fn();
        } catch (error) {
            throw HttpGameAdapter.toAdapterError(error, context);
        }
    }
}
