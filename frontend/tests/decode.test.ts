import { describe, expect, it } from 'vitest';
import type { components } from '../src/contract/generated/api';
import { assertRulesets, decodeSnapshotBody, TransportFailure } from '../src/gateway/decode';
import { decodeProblem } from '../src/gateway/problems';

type GameSnapshot = components['schemas']['GameSnapshot'];

const validSnapshot = {
    gameId: 'Zk3vQ1hYc0a7Tn2LwR9sXg',
    version: 1,
    serverTime: '2026-09-20T12:00:00.000Z',
    rulesetId: 'sea-battle-10-ship.v1',
    phase: 'WAITING',
    you: { displayName: 'Captain', ready: false, connected: true, shipsRemaining: 4 },
    allowedActions: ['NEW_INVITATION'],
    expiresAt: '2026-09-20T12:15:00.000Z',
    invitationUrl: 'http://localhost:4173/join/Zk3vQ1hYc0a7Tn2LwR9sXg#invite=secret',
    invitationExpiresAt: '2026-09-20T12:01:00.000Z',
    yourBoard: { grid: [['WATER']], ships: [] },
    opponentBoard: { grid: [['UNKNOWN']], ships: [] },
} satisfies GameSnapshot;

describe('decodeSnapshotBody', () => {
    it('rejects an HTML error page instead of storing it as a snapshot', () => {
        expect(() => decodeSnapshotBody('<html>Bad gateway</html>', 'text/html')).toThrow(TransportFailure);
    });

    it('rejects truncated JSON', () => {
        expect(() => decodeSnapshotBody('{"gameId":"abc"', 'application/json')).toThrow(TransportFailure);
    });

    it('rejects JSON without required snapshot keys', () => {
        expect(() => decodeSnapshotBody('{"gameId":"abc","phase":"WAITING"}', 'application/json')).toThrow(
            TransportFailure,
        );
    });

    it.each([
        ['an unknown phase', { phase: 'TELEPORTING' }],
        ['a missing you object', { you: null }],
        ['a you without a ship count', { you: { displayName: 'Captain', ready: false, connected: true } }],
        ['a board grid that is not a grid', { yourBoard: { grid: 'WATER', ships: [] } }],
        ['a board grid with an unknown cell state', { opponentBoard: { grid: [['LAVA']], ships: [] } }],
        ['a ship without cells', { yourBoard: { grid: [['WATER']], ships: [{ shipId: 'a' }] } }],
        ['a malformed last shot', { lastShot: { by: 'YOU' } }],
    ])('treats %s as an unreadable payload', (_label, override) => {
        const body = JSON.stringify({ ...validSnapshot, ...override });

        expect(() => decodeSnapshotBody(body, 'application/json')).toThrow(TransportFailure);
    });

    it('accepts a readable snapshot and drops unknown allowed actions', () => {
        const body = JSON.stringify({ ...validSnapshot, allowedActions: ['NEW_INVITATION', 'FUTURE_ACTION'] });

        expect(decodeSnapshotBody(body, 'application/json')).toEqual({
            ...validSnapshot,
            allowedActions: ['NEW_INVITATION'],
        });
    });
});

describe('assertRulesets', () => {
    const ruleset = {
        id: 'sea-battle-10-ship.v1',
        board: { rows: 10, columns: 10 },
        fleet: [{ shipTypeId: 'destroyer', length: 2, count: 1 }],
        shipsMayTouch: false,
        extraTurnOnHit: true,
        revealWaterAroundSunk: true,
    };

    it('returns a readable ruleset list', () => {
        expect(assertRulesets({ rulesets: [ruleset] })).toEqual([ruleset]);
    });

    it('treats a ruleset without a board as an unreadable payload', () => {
        expect(() => assertRulesets({ rulesets: [{ ...ruleset, board: undefined }] })).toThrow(TransportFailure);
    });
});

describe('decodeProblem', () => {
    it('rejects an unreadable HTML 502 instead of treating it as a definite refusal', () => {
        expect(() => decodeProblem(502, '<html>Bad gateway</html>', 'text/html')).toThrow(TransportFailure);
    });

    it('uses the status fallback for a readable problem with a future code', () => {
        const body = JSON.stringify({ title: 'Unavailable', status: 503, code: 'future-service-failure' });

        expect(decodeProblem(503, body, 'application/problem+json').messageKey).toBe('problem.serviceUnavailable');
    });
});
