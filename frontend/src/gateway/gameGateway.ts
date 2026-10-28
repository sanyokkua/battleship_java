import type { components } from '../contract/generated/api';
import { client } from './client';
import { decodeSnapshotBody, TransportFailure } from './decode';
import { decodeProblem, type GatewayProblem } from './problems';
import { getGameStore, type GameId, type Meta } from '../session/gameStore';
import { readCurrentGame, rememberCurrentGame } from '../session/currentGame';

export type Ruleset = components['schemas']['Ruleset'];
export class GatewayError extends Error {
    constructor(readonly problem: GatewayProblem) {
        super(problem.messageKey);
    }
}

function responseBody(data: unknown, error: unknown): string {
    const body = data ?? error ?? '';
    return typeof body === 'string' ? body : JSON.stringify(body);
}

async function requestMeta(): Promise<Meta> {
    const { data, response } = await client.GET('/api/v1/meta');
    if (!response.ok || data === undefined) throw new TransportFailure();
    return data;
}

let metaRequest: Promise<Meta> | undefined;
export function getMeta(refresh = false): Promise<Meta> {
    if (refresh || !metaRequest) {
        metaRequest = requestMeta().catch((error: unknown) => {
            metaRequest = undefined;
            throw error;
        });
    }
    return metaRequest;
}

export async function listRulesets(): Promise<Ruleset[]> {
    const { data, response } = await client.GET('/api/v1/rulesets');
    if (!response.ok || data === undefined) throw new TransportFailure();
    return data.rulesets;
}

function snapshotResponse(response: Response, body: string): ReturnType<typeof decodeSnapshotBody> {
    if (!response.ok) {
        throw new GatewayError(decodeProblem(response.status, body, response.headers.get('content-type') ?? ''));
    }
    return decodeSnapshotBody(body, response.headers.get('content-type') ?? '');
}

async function createRequest(
    request: components['schemas']['CreateGameRequest'],
): Promise<ReturnType<typeof decodeSnapshotBody>> {
    const result = await client.POST('/api/v1/games', { body: request, parseAs: 'text' });
    const body = responseBody(result.data, result.error);
    return snapshotResponse(result.response, body);
}

async function joinRequest(
    gameId: GameId,
    request: components['schemas']['JoinGameRequest'],
): Promise<ReturnType<typeof decodeSnapshotBody>> {
    const result = await client.POST('/api/v1/games/{gameId}/join', {
        params: { path: { gameId } },
        body: request,
        parseAs: 'text',
    });
    return snapshotResponse(result.response, responseBody(result.data, result.error));
}

export async function createGame(
    request: components['schemas']['CreateGameRequest'],
): Promise<ReturnType<typeof decodeSnapshotBody>> {
    await getMeta();
    let result: Awaited<ReturnType<typeof createRequest>>;
    try {
        result = await createRequest(request);
    } catch (error) {
        if (!(error instanceof GatewayError) || error.problem.code !== 'request-security-rejected') throw error;
        await getMeta(true);
        result = await createRequest(request);
    }
    const gameId = result.gameId;
    rememberCurrentGame(gameId);
    getGameStore(gameId).acceptSnapshot(result);
    return result;
}

export async function joinGame(
    gameId: GameId,
    request: components['schemas']['JoinGameRequest'],
): Promise<ReturnType<typeof decodeSnapshotBody>> {
    await getMeta();
    let result: Awaited<ReturnType<typeof joinRequest>>;
    try {
        result = await joinRequest(gameId, request);
    } catch (error) {
        if (!(error instanceof GatewayError) || error.problem.code !== 'request-security-rejected') throw error;
        await getMeta(true);
        result = await joinRequest(gameId, request);
    }
    rememberCurrentGame(gameId);
    getGameStore(gameId).acceptSnapshot(result);
    return result;
}

export async function getGame(gameId: GameId) {
    const result = await client.GET('/api/v1/games/{gameId}', { params: { path: { gameId } }, parseAs: 'text' });
    const body = responseBody(result.data, result.error);
    try {
        const snapshot = snapshotResponse(result.response, body);
        getGameStore(gameId).acceptSnapshot(snapshot);
        return snapshot;
    } catch (error) {
        if (
            error instanceof GatewayError &&
            [401, 404, 410].includes(error.problem.status) &&
            readCurrentGame() === gameId
        ) {
            rememberCurrentGame(undefined);
        }
        throw error;
    }
}

async function snapshotPost(
    path: '/api/v1/games/{gameId}/invitation' | '/api/v1/games/{gameId}/presence',
    gameId: GameId,
) {
    await getMeta();
    const send = async () => {
        const result = await client.POST(path, { params: { path: { gameId } }, parseAs: 'text' });
        return snapshotResponse(result.response, responseBody(result.data, result.error));
    };
    try {
        const snapshot = await send();
        getGameStore(gameId).acceptSnapshot(snapshot);
        return snapshot;
    } catch (error) {
        if (!(error instanceof GatewayError) || error.problem.code !== 'request-security-rejected') throw error;
        await getMeta(true);
        const snapshot = await send();
        getGameStore(gameId).acceptSnapshot(snapshot);
        return snapshot;
    }
}

export function replaceInvitation(gameId: GameId) {
    return snapshotPost('/api/v1/games/{gameId}/invitation', gameId);
}

export function sendPresence(gameId: GameId) {
    return snapshotPost('/api/v1/games/{gameId}/presence', gameId);
}

export async function sendCommand(
    gameId: GameId,
    request: components['schemas']['CommandRequest'],
    signal?: AbortSignal,
) {
    await getMeta();
    const send = async () => {
        const result = await client.POST('/api/v1/games/{gameId}/commands', {
            params: { path: { gameId } },
            body: request,
            parseAs: 'text',
            ...(signal ? { signal } : {}),
        });
        return snapshotResponse(result.response, responseBody(result.data, result.error));
    };
    try {
        let snapshot;
        try {
            snapshot = await send();
        } catch (error) {
            if (!(error instanceof GatewayError) || error.problem.code !== 'request-security-rejected') throw error;
            await getMeta(true);
            snapshot = await send();
        }
        getGameStore(gameId).acceptSnapshot(snapshot);
        return snapshot;
    } catch (error) {
        if (error instanceof GatewayError && error.problem.status === 409) await getGame(gameId);
        throw error;
    }
}
