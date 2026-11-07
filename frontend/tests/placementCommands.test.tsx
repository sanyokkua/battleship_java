import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { components } from '../src/contract/generated/api';
import type { Ruleset } from '../src/gateway/gameGateway';
import { getGameStore } from '../src/session/gameStore';
import { Placement } from '../src/screens/Placement';

type GameSnapshot = components['schemas']['GameSnapshot'];

const ruleset: Ruleset = {
    id: 'tiny',
    board: { rows: 2, columns: 2 },
    fleet: [{ shipTypeId: 'dinghy', length: 1, count: 1 }],
    shipsMayTouch: true,
    extraTurnOnHit: false,
    revealWaterAroundSunk: false,
};

function snapshot(gameId: string): GameSnapshot {
    return {
        gameId,
        version: 1,
        serverTime: '2026-09-20T12:00:00.000Z',
        rulesetId: 'tiny',
        phase: 'PLACEMENT',
        you: { displayName: 'Captain', ready: false, connected: true, shipsRemaining: 1 },
        allowedActions: ['PLACE_SHIP', 'REMOVE_SHIP', 'PLACE_FLEET_RANDOMLY', 'CLEAR_FLEET'],
        expiresAt: '2026-09-20T12:15:00.000Z',
        yourBoard: {
            grid: [
                ['WATER', 'WATER'],
                ['WATER', 'WATER'],
            ],
            ships: [],
        },
        opponentBoard: {
            grid: [
                ['UNKNOWN', 'UNKNOWN'],
                ['UNKNOWN', 'UNKNOWN'],
            ],
            ships: [],
        },
    };
}

beforeEach(() => {
    vi.stubGlobal('matchMedia', () => Object.assign(new EventTarget(), { matches: false }));
});
afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
});

function mount(gameId: string) {
    const store = getGameStore(gameId);
    store.acceptSnapshot(snapshot(gameId));
    const onCommand = vi.fn().mockResolvedValue(undefined);
    render(
        <Placement
            gameId={gameId}
            leaveSlotRef={null}
            ruleset={ruleset}
            streamReplaced={false}
            onCommand={onCommand}
        />,
    );
    return { store, onCommand };
}

// R19: placement commands go through the action machinery (one in flight, identified), not a private sender.
it('registers a placement command as the single in-flight action and hands it to the shared sender', async () => {
    const { store, onCommand } = mount('PlacementActionGame00001');

    await userEvent.click(screen.getByRole('button', { name: 'Random' }));

    expect(onCommand).toHaveBeenCalledOnce();
    const request = onCommand.mock.calls[0]?.[0] as components['schemas']['CommandRequest'];
    expect(request.command).toEqual({ type: 'PLACE_FLEET_RANDOMLY' });
    expect(store.getSnapshot().action).toEqual(request);
    expect(screen.getByRole('button', { name: 'Random' })).toBeDisabled();
});

// R22: a rate-limit wait hint keeps placement controls disabled until it elapses.
it('keeps placement controls disabled until the server wait hint elapses', () => {
    vi.useFakeTimers();
    const gameId = 'PlacementRetryGame00001';
    const { store } = mount(gameId);

    act(() => {
        store.setNotice({
            status: 429,
            code: 'rate-limit-exceeded',
            messageKey: 'problem.rateLimited',
            retryAfterSeconds: 5,
        });
    });
    expect(screen.getByRole('button', { name: 'Random' })).toBeDisabled();

    act(() => {
        vi.advanceTimersByTime(5100);
    });
    expect(screen.getByRole('button', { name: 'Random' })).toBeEnabled();
});
