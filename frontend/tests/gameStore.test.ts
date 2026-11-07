import { describe, expect, it, vi } from 'vitest';
import { createGameStore, discardGameStore, getGameStore } from '../src/session/gameStore';
import type { components } from '../src/contract/generated/api';

type GameSnapshot = components['schemas']['GameSnapshot'];
const snapshot = (version: number): GameSnapshot => ({
    gameId: 'Zk3vQ1hYc0a7Tn2LwR9sXg',
    version,
    serverTime: '2026-09-20T12:00:00.000Z',
    rulesetId: 'sea-battle-10-ship.v1',
    phase: 'WAITING',
    you: { displayName: 'Captain', ready: false, connected: true, shipsRemaining: 4 },
    allowedActions: [],
    expiresAt: '2026-09-20T12:15:00.000Z',
    yourBoard: { grid: [['WATER']], ships: [] },
    opponentBoard: { grid: [['UNKNOWN']], ships: [] },
});

describe('game store', () => {
    it('publishes only accepted snapshots and synchronizes its server clock', () => {
        const store = createGameStore();
        let changes = 0;
        store.subscribe(() => changes++);
        const serverTime = Date.parse('2026-09-20T12:00:00.000Z');
        const before = Date.now();
        store.acceptSnapshot(snapshot(2));
        const after = Date.now();
        store.acceptSnapshot(snapshot(1));
        expect(store.getSnapshot().snapshot?.version).toBe(2);
        expect(store.getSnapshot().clockOffsetMs).toBeGreaterThanOrEqual(serverTime - after);
        expect(store.getSnapshot().clockOffsetMs).toBeLessThanOrEqual(serverTime - before);
        expect(changes).toBe(1);
    });

    it('stores a retry notice with an absolute retry time', () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date('2026-10-28T12:00:00.000Z'));
        const store = createGameStore();
        store.setNotice({
            status: 429,
            code: 'rate-limit-exceeded',
            messageKey: 'problem.rateLimited',
            retryAfterSeconds: 3,
        });

        expect(store.getSnapshot().notice?.retryAtMs).toBe(Date.parse('2026-10-28T12:00:03.000Z'));
        vi.useRealTimers();
    });

    // R27: a lost session must not keep its snapshot (incl. invitation secret) or notices in memory.
    it('discards a game presentation state when the session is lost', () => {
        const store = getGameStore('Zk3vQ1hYc0a7Tn2LwR9sXg');
        store.acceptSnapshot({ ...snapshot(2), invitationUrl: 'http://localhost/join/x#invite=secret' });
        store.setNotice({ status: 429, code: 'rate-limit-exceeded', messageKey: 'problem.rateLimited' });
        let changes = 0;
        store.subscribe(() => changes++);

        discardGameStore('Zk3vQ1hYc0a7Tn2LwR9sXg');

        expect(store.getSnapshot().snapshot).toBeUndefined();
        expect(store.getSnapshot().notice).toBeUndefined();
        expect(changes).toBe(1);
        expect(getGameStore('Zk3vQ1hYc0a7Tn2LwR9sXg')).not.toBe(store);
        expect(getGameStore('Zk3vQ1hYc0a7Tn2LwR9sXg').getSnapshot().snapshot).toBeUndefined();
    });
});
