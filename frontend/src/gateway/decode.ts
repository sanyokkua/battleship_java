import {
    actionValues,
    cellStateValues,
    phaseValues,
    shipStatusValues,
    shotResultValues,
    sideValues,
    type components,
} from '../contract/generated/api';

type GameSnapshot = components['schemas']['GameSnapshot'];
type Action = components['schemas']['Action'];
type Ruleset = components['schemas']['Ruleset'];

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

    if (!isRecord(value) || !isSnapshotShape(value)) throw new TransportFailure();

    const allowedActions = Array.isArray(value.allowedActions)
        ? value.allowedActions.filter((action): action is Action => actionValues.includes(action as Action))
        : [];

    return { ...value, allowedActions } as GameSnapshot;
}

export function assertRulesets(value: unknown): Ruleset[] {
    if (!isRecord(value) || !Array.isArray(value.rulesets) || !value.rulesets.every(isRuleset)) {
        throw new TransportFailure();
    }
    return value.rulesets as Ruleset[];
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

const isString = (value: unknown): value is string => typeof value === 'string';
const isNumber = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value);
const isOneOf = (values: readonly unknown[]) => (value: unknown) => values.includes(value);
const isArrayOf = (check: (item: unknown) => boolean) => (value: unknown) => Array.isArray(value) && value.every(check);
const optional = (check: (value: unknown) => boolean) => (value: unknown) => value === undefined || check(value);

const isCoordinate = (value: unknown) => isRecord(value) && isNumber(value.rowIndex) && isNumber(value.columnIndex);
const isPlayer = (value: unknown) =>
    isRecord(value) &&
    isString(value.displayName) &&
    typeof value.ready === 'boolean' &&
    typeof value.connected === 'boolean' &&
    isNumber(value.shipsRemaining);
const isShip = (value: unknown) =>
    isRecord(value) &&
    isString(value.shipId) &&
    isString(value.shipTypeId) &&
    isNumber(value.length) &&
    isArrayOf(isCoordinate)(value.cells) &&
    isOneOf(shipStatusValues)(value.status);
const isBoard = (value: unknown) =>
    isRecord(value) && isArrayOf(isArrayOf(isOneOf(cellStateValues)))(value.grid) && isArrayOf(isShip)(value.ships);
const isShot = (value: unknown) =>
    isRecord(value) &&
    isOneOf(sideValues)(value.by) &&
    isCoordinate(value.target) &&
    isOneOf(shotResultValues)(value.result);
const isRuleset = (value: unknown) =>
    isRecord(value) &&
    isString(value.id) &&
    isRecord(value.board) &&
    isNumber(value.board.rows) &&
    isNumber(value.board.columns) &&
    isArrayOf(
        (entry) => isRecord(entry) && isString(entry.shipTypeId) && isNumber(entry.length) && isNumber(entry.count),
    )(value.fleet) &&
    typeof value.shipsMayTouch === 'boolean' &&
    typeof value.extraTurnOnHit === 'boolean' &&
    typeof value.revealWaterAroundSunk === 'boolean';

function isSnapshotShape(record: Record<string, unknown>): boolean {
    return (
        REQUIRED_SNAPSHOT_KEYS.every((key) => Object.hasOwn(record, key)) &&
        isString(record.gameId) &&
        isNumber(record.version) &&
        isString(record.serverTime) &&
        isString(record.rulesetId) &&
        isOneOf(phaseValues)(record.phase) &&
        isPlayer(record.you) &&
        optional(isPlayer)(record.opponent) &&
        optional(isOneOf(sideValues))(record.turn) &&
        Array.isArray(record.allowedActions) &&
        isString(record.expiresAt) &&
        isBoard(record.yourBoard) &&
        isBoard(record.opponentBoard) &&
        optional(isShot)(record.lastShot)
    );
}
