import {beforeEach, describe, expect, it, vi} from "vitest";
import type {AxiosError} from "axios";
import {HttpGameAdapter} from "./HttpGameAdapter";
import {isGameAdapterError} from "./AdapterErrors";
import * as BackendRequestService from "../services/BackendRequestService";
import type {
    ResponseCreatedPlayerDto,
    ResponseGameplayStateDto,
    ResponseOpponentInformationDto,
    ResponsePlayerReady,
    ResponsePreparationState,
    ResponseShipAddedDto,
    ResponseShipRemovedDto,
    ResponseShotResultDto
} from "../logic/ApplicationTypes";

vi.mock("../services/BackendRequestService");

const mockedService = vi.mocked(BackendRequestService);

function axiosErrorWith(status: number, data: unknown): AxiosError {
    const error = new Error("Request failed") as AxiosError;
    error.isAxiosError = true;
    error.response = {
        status,
        data,
        statusText: "Error",
        headers: {},
        config: {} as never
    } as AxiosError["response"];
    return error;
}

describe("HttpGameAdapter", () => {
    let adapter: HttpGameAdapter;

    beforeEach(() => {
        vi.resetAllMocks();
        adapter = new HttpGameAdapter();
    });

    it("getEditions delegates to getAvailableGameEditions and unwraps gameEditions", async () => {
        mockedService.getAvailableGameEditions.mockResolvedValue({gameEditions: ["UKRAINIAN", "MILTON_BRADLEY"]});

        const result = await adapter.getEditions();

        expect(result).toEqual(["UKRAINIAN", "MILTON_BRADLEY"]);
        expect(mockedService.getAvailableGameEditions).toHaveBeenCalledTimes(1);
    });

    it("createSession delegates to createGameSession and unwraps sessionId", async () => {
        mockedService.createGameSession.mockResolvedValue({sessionId: "session-1"});

        const result = await adapter.createSession("UKRAINIAN");

        expect(result).toBe("session-1");
        expect(mockedService.createGameSession).toHaveBeenCalledWith("UKRAINIAN");
    });

    it("createPlayer delegates to createPlayerInSession", async () => {
        const dto: ResponseCreatedPlayerDto = {playerId: "p1", playerName: "Alice"};
        mockedService.createPlayerInSession.mockResolvedValue(dto);

        const result = await adapter.createPlayer("session-1", "Alice");

        expect(result).toEqual(dto);
        expect(mockedService.createPlayerInSession).toHaveBeenCalledWith("session-1", "Alice");
    });

    it("getStage delegates to getCurrentGameStage and unwraps gameStage", async () => {
        mockedService.getCurrentGameStage.mockResolvedValue({gameStage: "PREPARATION"});

        const result = await adapter.getStage("session-1");

        expect(result).toBe("PREPARATION");
        expect(mockedService.getCurrentGameStage).toHaveBeenCalledWith("session-1");
    });

    it("getChangeTime delegates to getLastSessionChangeTime and unwraps lastId", async () => {
        mockedService.getLastSessionChangeTime.mockResolvedValue({lastId: "42"});

        const result = await adapter.getChangeTime("session-1");

        expect(result).toBe("42");
    });

    it("getPreparationState delegates to getPreparationState", async () => {
        const dto: ResponsePreparationState = {ships: [], field: []};
        mockedService.getPreparationState.mockResolvedValue(dto);

        const result = await adapter.getPreparationState("session-1", "p1");

        expect(result).toBe(dto);
        expect(mockedService.getPreparationState).toHaveBeenCalledWith("session-1", "p1");
    });

    it("addShip delegates to addShipToField with the given args", async () => {
        const dto: ResponseShipAddedDto = {shipId: "ship-1"};
        mockedService.addShipToField.mockResolvedValue(dto);

        const result = await adapter.addShip("session-1", "p1", "ship-1", {row: 0, column: 0}, "HORIZONTAL");

        expect(result).toBe(dto);
        expect(mockedService.addShipToField).toHaveBeenCalledWith("session-1", "p1", "ship-1", {row: 0, column: 0}, "HORIZONTAL");
    });

    it("removeShip delegates to removeShipFromField", async () => {
        const dto: ResponseShipRemovedDto = {deleted: true};
        mockedService.removeShipFromField.mockResolvedValue(dto);

        const result = await adapter.removeShip("session-1", "p1", {row: 1, column: 1});

        expect(result).toBe(dto);
        expect(mockedService.removeShipFromField).toHaveBeenCalledWith("session-1", "p1", {row: 1, column: 1});
    });

    it("getOpponent delegates to getOpponentInformation", async () => {
        const dto: ResponseOpponentInformationDto = {playerName: "Bob", ready: false};
        mockedService.getOpponentInformation.mockResolvedValue(dto);

        const result = await adapter.getOpponent("session-1", "p1");

        expect(result).toBe(dto);
    });

    it("setReady delegates to startGame", async () => {
        const dto: ResponsePlayerReady = {ready: true};
        mockedService.startGame.mockResolvedValue(dto);

        const result = await adapter.setReady("session-1", "p1");

        expect(result).toBe(dto);
        expect(mockedService.startGame).toHaveBeenCalledWith("session-1", "p1");
    });

    it("getGameState delegates to getGameStateForPlayer", async () => {
        const dto = {} as ResponseGameplayStateDto;
        mockedService.getGameStateForPlayer.mockResolvedValue(dto);

        const result = await adapter.getGameState("session-1", "p1");

        expect(result).toBe(dto);
    });

    it("shoot delegates to makeShotByField", async () => {
        const dto: ResponseShotResultDto = {shotResult: "HIT"};
        mockedService.makeShotByField.mockResolvedValue(dto);

        const result = await adapter.shoot("session-1", "p1", {row: 2, column: 3});

        expect(result).toBe(dto);
        expect(mockedService.makeShotByField).toHaveBeenCalledWith("session-1", "p1", {row: 2, column: 3});
    });

    it("throws GameAdapterError with httpStatus/errorCode/message from the response body", async () => {
        mockedService.createGameSession.mockRejectedValue(
            axiosErrorWith(400, {status: 400, errorMessage: "Edition is not valid", errorCode: "EDITION_INVALID"})
        );

        await expect(adapter.createSession("NOT_AN_EDITION")).rejects.toSatisfy((err: unknown) => {
            expect(isGameAdapterError(err)).toBe(true);
            if (!isGameAdapterError(err)) return false;
            expect(err.httpStatus).toBe(400);
            expect(err.errorCode).toBe("EDITION_INVALID");
            expect(err.message).toBe("Edition is not valid");
            expect(err.context).toContain("createSession");
            return true;
        });
    });

    it("tolerates a missing errorCode on the response body (older/stale backend)", async () => {
        mockedService.createPlayerInSession.mockRejectedValue(
            axiosErrorWith(400, {status: 400, errorMessage: "Player name is not valid"})
        );

        await expect(adapter.createPlayer("session-1", "x")).rejects.toSatisfy((err: unknown) => {
            expect(isGameAdapterError(err)).toBe(true);
            if (!isGameAdapterError(err)) return false;
            expect(err.httpStatus).toBe(400);
            expect(err.errorCode).toBeUndefined();
            expect(err.message).toBe("Player name is not valid");
            return true;
        });
    });

    it("falls back to the axios error message when there is no response body at all", async () => {
        const networkError = new Error("Network Error") as AxiosError;
        networkError.isAxiosError = true;
        mockedService.getAvailableGameEditions.mockRejectedValue(networkError);

        await expect(adapter.getEditions()).rejects.toSatisfy((err: unknown) => {
            expect(isGameAdapterError(err)).toBe(true);
            if (!isGameAdapterError(err)) return false;
            expect(err.httpStatus).toBeUndefined();
            expect(err.errorCode).toBeUndefined();
            expect(err.message).toBe("Network Error");
            return true;
        });
    });
});
