import { describe, expect, it } from 'vitest';
import type { components } from '../src/contract/generated/api';
import { snapshotGate } from '../src/session/snapshotGate';

type GameSnapshot = components['schemas']['GameSnapshot'];

function snapshot(version: number): GameSnapshot {
    return {
        gameId: 'Zk3vQ1hYc0a7Tn2LwR9sXg',
        version,
        serverTime: '2026-09-20T12:00:00.000Z',
        rulesetId: 'sea-battle-10-ship.v1',
        phase: 'WAITING',
        you: { displayName: 'Captain', ready: false, connected: true, shipsRemaining: 4 },
        allowedActions: [],
        expiresAt: '2026-09-20T12:15:00.000Z',
        invitationUrl: 'http://localhost:4173/join/Zk3vQ1hYc0a7Tn2LwR9sXg#invite=secret',
        invitationExpiresAt: '2026-09-20T12:01:00.000Z',
        yourBoard: { grid: [['WATER']], ships: [] },
        opponentBoard: { grid: [['UNKNOWN']], ships: [] },
    };
}

describe('snapshotGate', () => {
    it('replaces the current view with a higher version', () => {
        const current = snapshot(1);
        const next = { ...snapshot(2), phase: 'PLACEMENT' as const };

        expect(snapshotGate.accept(current, next)).toBe(next);
    });

    it('keeps board and version at an equal version but refreshes time and invitation fields', () => {
        const current = snapshot(3);
        const next = {
            ...snapshot(3),
            serverTime: '2026-09-20T12:00:05.000Z',
            expiresAt: '2026-09-20T12:16:00.000Z',
            invitationUrl: 'http://localhost:4173/join/new#invite=new-secret',
            invitationExpiresAt: '2026-09-20T12:02:00.000Z',
            yourBoard: { grid: [['SHIP' as const]], ships: [] },
        };

        expect(snapshotGate.accept(current, next)).toEqual({
            ...current,
            serverTime: next.serverTime,
            expiresAt: next.expiresAt,
            invitationUrl: next.invitationUrl,
            invitationExpiresAt: next.invitationExpiresAt,
        });
    });

    it('clears invitation fields when an equal-version view omits them', () => {
        const current = snapshot(3);
        const next = snapshot(3);
        delete next.invitationUrl;
        delete next.invitationExpiresAt;

        const accepted = snapshotGate.accept(current, next);

        expect(accepted).not.toHaveProperty('invitationUrl');
        expect(accepted).not.toHaveProperty('invitationExpiresAt');
    });

    it('drops a lower version and keeps the current snapshot', () => {
        const current = snapshot(4);

        expect(snapshotGate.accept(current, snapshot(2))).toBe(current);
    });
});
