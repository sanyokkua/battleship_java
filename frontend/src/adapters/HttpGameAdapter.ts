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
    async getEditions(): Promise<string[]> {
        return this.wrap("getEditions()", async () => {
            const dto = await getAvailableGameEditions();
            return dto.gameEditions;
        });
    }

    async createSession(edition: string): Promise<string> {
        return this.wrap(`createSession(edition=${edition})`, async () => {
            const dto = await createGameSession(edition);
            return dto.sessionId;
        });
    }

    async createPlayer(sessionId: string, name: string): Promise<ResponseCreatedPlayerDto> {
        return this.wrap(`createPlayer(sessionId=${sessionId}, name=${name})`, () =>
            createPlayerInSession(sessionId, name));
    }

    async getStage(sessionId: string): Promise<string> {
        return this.wrap(`getStage(sessionId=${sessionId})`, async () => {
            const dto = await getCurrentGameStage(sessionId);
            return dto.gameStage;
        });
    }

    async getChangeTime(sessionId: string): Promise<string> {
        return this.wrap(`getChangeTime(sessionId=${sessionId})`, async () => {
            const dto = await getLastSessionChangeTime(sessionId);
            return dto.lastId;
        });
    }

    async getPreparationState(sessionId: string, playerId: string): Promise<ResponsePreparationState> {
        return this.wrap(`getPreparationState(sessionId=${sessionId}, playerId=${playerId})`, () =>
            getPreparationState(sessionId, playerId));
    }

    async addShip(sessionId: string, playerId: string, shipId: string, at: Coordinate, dir: ShipDirection): Promise<ResponseShipAddedDto> {
        return this.wrap(`addShip(sessionId=${sessionId}, playerId=${playerId}, shipId=${shipId}, at=${JSON.stringify(at)}, dir=${dir})`, () =>
            addShipToField(sessionId, playerId, shipId, at, dir));
    }

    async removeShip(sessionId: string, playerId: string, at: Coordinate): Promise<ResponseShipRemovedDto> {
        return this.wrap(`removeShip(sessionId=${sessionId}, playerId=${playerId}, at=${JSON.stringify(at)})`, () =>
            removeShipFromField(sessionId, playerId, at));
    }

    async getOpponent(sessionId: string, playerId: string): Promise<ResponseOpponentInformationDto> {
        return this.wrap(`getOpponent(sessionId=${sessionId}, playerId=${playerId})`, () =>
            getOpponentInformation(sessionId, playerId));
    }

    async setReady(sessionId: string, playerId: string): Promise<ResponsePlayerReady> {
        return this.wrap(`setReady(sessionId=${sessionId}, playerId=${playerId})`, () =>
            startGame(sessionId, playerId));
    }

    async getGameState(sessionId: string, playerId: string): Promise<ResponseGameplayStateDto> {
        return this.wrap(`getGameState(sessionId=${sessionId}, playerId=${playerId})`, () =>
            getGameStateForPlayer(sessionId, playerId));
    }

    async shoot(sessionId: string, playerId: string, at: Coordinate): Promise<ResponseShotResultDto> {
        return this.wrap(`shoot(sessionId=${sessionId}, playerId=${playerId}, at=${JSON.stringify(at)})`, () =>
            makeShotByField(sessionId, playerId, at));
    }

    private async wrap<T>(context: string, fn: () => Promise<T>): Promise<T> {
        try {
            return await fn();
        } catch (error) {
            throw HttpGameAdapter.toAdapterError(error, context);
        }
    }

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
}
