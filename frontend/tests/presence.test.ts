import { afterEach, expect, it, vi } from 'vitest';
import type { components } from '../src/contract/generated/api';
import { sendPresence } from '../src/gateway/gameGateway';
import { getGameStore } from '../src/session/gameStore';
import { startPresence } from '../src/session/presence';

vi.mock('../src/gateway/gameGateway', () => ({ sendPresence: vi.fn().mockResolvedValue({}) }));

type GameSnapshot = components['schemas']['GameSnapshot'];

const snapshot = {
    gameId: 'PresenceGame00000000001',
    version: 1,
    serverTime: '2026-09-20T12:00:00.000Z',
    rulesetId: 'sea-battle-10-ship.v1',
    phase: 'PLAYING',
    you: { displayName: 'Captain', ready: true, connected: true, shipsRemaining: 4 },
    allowedActions: ['SEND_PRESENCE'],
    expiresAt: '2026-09-20T12:15:00.000Z',
    yourBoard: { grid: [['WATER']], ships: [] },
    opponentBoard: { grid: [['UNKNOWN']], ships: [] },
} satisfies GameSnapshot;

afterEach(() => {
    vi.useRealTimers();
    vi.mocked(sendPresence).mockClear();
});

// Re-arming the timer on every store publish would starve presence during active play.
it('sends presence on schedule even when the store publishes more often than the interval', () => {
    vi.useFakeTimers();
    const store = getGameStore(snapshot.gameId);
    store.acceptSnapshot(snapshot);
    const stop = startPresence(snapshot.gameId, 10);

    for (let second = 0; second < 25; second++) {
        vi.advanceTimersByTime(1000);
        store.setNotice(undefined);
    }

    expect(sendPresence).toHaveBeenCalledTimes(2);
    stop();
});

it('stops sending once SEND_PRESENCE is no longer allowed', () => {
    vi.useFakeTimers();
    const store = getGameStore('PresenceGame00000000002');
    store.acceptSnapshot({ ...snapshot, gameId: 'PresenceGame00000000002' });
    const stop = startPresence('PresenceGame00000000002', 10);

    store.acceptSnapshot({
        ...snapshot,
        gameId: 'PresenceGame00000000002',
        version: 2,
        allowedActions: [],
    });
    vi.advanceTimersByTime(30_000);

    expect(sendPresence).not.toHaveBeenCalled();
    stop();
});
