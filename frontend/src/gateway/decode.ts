import { actionValues, type components } from '../contract/generated/api';

type GameSnapshot = components['schemas']['GameSnapshot'];
type Action = components['schemas']['Action'];

const REQUIRED_SNAPSHOT_KEYS = [
    'gameId',
    'version',
    'serverTime',
    'rulesetId',
    'phase',
    'you',
    'allowedActions',
    'expiresAt',
    'yourBoard',
    'opponentBoard',
] as const satisfies readonly (keyof GameSnapshot)[];

export class TransportFailure extends Error {
    constructor() {
        super('The server response could not be read.');
        this.name = 'TransportFailure';
    }
}

export function decodeSnapshotBody(body: string, contentType: string): GameSnapshot {
    const mediaType = contentType.split(';', 1)[0]?.trim().toLowerCase();
    if (mediaType !== 'application/json' && mediaType !== 'application/problem+json') {
        throw new TransportFailure();
    }

    let value: unknown;
    try {
        value = JSON.parse(body);
    } catch {
        throw new TransportFailure();
    }

    if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new TransportFailure();
    const record = value as Record<string, unknown>;
    if (REQUIRED_SNAPSHOT_KEYS.some((key) => !Object.hasOwn(record, key))) throw new TransportFailure();

    const allowedActions = Array.isArray(record.allowedActions)
        ? record.allowedActions.filter((action): action is Action => actionValues.includes(action as Action))
        : [];

    return { ...record, allowedActions } as GameSnapshot;
}
