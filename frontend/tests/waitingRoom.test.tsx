import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { components } from '../src/contract/generated/api';
import { replaceInvitation } from '../src/gateway/gameGateway';
import { getGameStore } from '../src/session/gameStore';
import { WaitingRoom } from '../src/screens/WaitingRoom';

vi.mock('../src/gateway/gameGateway', () => ({
    GatewayError: class GatewayError extends Error {
        constructor(readonly problem: { status: number; code: string; messageKey: string }) {
            super();
        }
    },
    getGame: vi.fn().mockResolvedValue({}),
    replaceInvitation: vi.fn(),
}));
vi.mock('../src/components/Qr', () => ({ Qr: () => null }));

type GameSnapshot = components['schemas']['GameSnapshot'];

const gameId = 'WaitingRoomGame0000001';
const snapshot: GameSnapshot = {
    gameId,
    version: 1,
    serverTime: '2099-09-20T12:00:00.000Z',
    rulesetId: 'tiny',
    phase: 'WAITING',
    you: { displayName: 'Captain', ready: false, connected: true, shipsRemaining: 1 },
    allowedActions: ['NEW_INVITATION'],
    expiresAt: '2099-09-20T12:15:00.000Z',
    invitationUrl: 'http://localhost/join/WaitingRoomGame0000001#invite=secret',
    invitationExpiresAt: '2099-09-20T12:01:00.000Z',
    yourBoard: { grid: [['WATER']], ships: [] },
    opponentBoard: { grid: [['UNKNOWN']], ships: [] },
};

beforeEach(() => {
    HTMLDialogElement.prototype.showModal = function () {
        this.open = true;
    };
    HTMLDialogElement.prototype.close = function () {
        this.open = false;
    };
    getGameStore(gameId).acceptSnapshot(snapshot);
});
afterEach(() => {
    vi.mocked(replaceInvitation).mockReset();
});

// A refused replacement must show the contract's mapped problem, not a generic failure toast.
it('shows the mapped problem when replacing the invitation is refused', async () => {
    const { GatewayError } = await import('../src/gateway/gameGateway');
    vi.mocked(replaceInvitation).mockRejectedValue(
        new GatewayError({ status: 429, code: 'rate-limit-exceeded', messageKey: 'problem.rateLimited' }),
    );
    render(<WaitingRoom gameId={gameId} leaveSlotRef={null} />);

    await userEvent.click(screen.getByRole('button', { name: 'Replace invitation' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Replace link' }));

    expect(getGameStore(gameId).getSnapshot().notice?.messageKey).toBe('problem.rateLimited');
    expect(screen.queryByText('Could not replace invitation link')).not.toBeInTheDocument();
});
