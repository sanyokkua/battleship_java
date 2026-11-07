import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { joinGame } from '../src/gateway/gameGateway';
import { JoinInvitation } from '../src/screens/JoinInvitation';
import { readInvitationSecret, stripInvitationSecret } from '../src/session/inviteSecret';

vi.mock('../src/gateway/gameGateway', () => ({
    GatewayError: class GatewayError extends Error {
        constructor(readonly problem: { status: number; code: string; messageKey: string }) {
            super();
        }
    },
    joinGame: vi.fn(),
}));

const gameId = 'JoinGame0000000000001';

beforeEach(() => {
    globalThis.history.replaceState(null, '', `/join/${gameId}#invite=the-secret`);
    stripInvitationSecret();
});
afterEach(() => {
    vi.mocked(joinGame).mockReset();
});

async function submit() {
    render(<JoinInvitation gameId={gameId} />);
    await userEvent.type(screen.getByLabelText('Captain name'), 'Guest');
    await userEvent.click(screen.getByRole('button', { name: 'Join game' }));
}

// An unknown outcome (lost answer, timeout) must not burn the secret nor claim the invitation is unavailable.
it('keeps the invitation secret and reports an unknown outcome when the answer is lost', async () => {
    vi.mocked(joinGame).mockRejectedValue(new TypeError('network down'));

    await submit();

    expect(await screen.findByRole('alert')).toHaveTextContent('server response was lost');
    expect(screen.queryByText(/invitation/i)).not.toBeInTheDocument();
    expect(readInvitationSecret()).toBe('the-secret');
    expect(screen.getByRole('button', { name: 'Join game' })).toBeEnabled();
});

it('discards the secret once the server says the invitation is unavailable', async () => {
    const { GatewayError } = await import('../src/gateway/gameGateway');
    vi.mocked(joinGame).mockRejectedValue(
        new GatewayError({ status: 404, code: 'invitation-unavailable', messageKey: 'problem.invitationUnavailable' }),
    );

    await submit();

    expect(await screen.findByRole('heading')).toHaveTextContent(/invitation/i);
    expect(readInvitationSecret()).toBeUndefined();
});

it('passes a timeout signal to the join request', async () => {
    vi.mocked(joinGame).mockRejectedValue(new TypeError('x'));

    await submit();

    expect(vi.mocked(joinGame).mock.calls[0]?.[2]).toBeInstanceOf(AbortSignal);
});
