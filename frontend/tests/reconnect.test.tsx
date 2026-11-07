import { act, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { getGame } from '../src/gateway/gameGateway';
import { GameRoute } from '../src/screens/GameRoute';

vi.mock('../src/gateway/gameGateway', () => ({
    GatewayError: class GatewayError extends Error {
        constructor(readonly problem: { status: number }) {
            super();
        }
    },
    getMeta: vi.fn().mockRejectedValue(new Error('meta unavailable')),
    listRulesets: vi.fn().mockRejectedValue(new Error('rulesets unavailable')),
    getGame: vi.fn().mockResolvedValue({}),
    sendCommand: vi.fn(),
    sendPresence: vi.fn(),
    leaveGame: vi.fn(),
}));

class QuietEventSource {
    static readonly CONNECTING = 0;
    static readonly OPEN = 1;
    static readonly CLOSED = 2;
    readyState = QuietEventSource.OPEN;
    onerror: unknown = null;
    addEventListener() {
        return undefined;
    }
    close() {
        this.readyState = QuietEventSource.CLOSED;
    }
}

beforeEach(() => {
    vi.stubGlobal('EventSource', QuietEventSource);
    vi.stubGlobal('matchMedia', () => Object.assign(new EventTarget(), { matches: false }));
});
afterEach(() => {
    vi.unstubAllGlobals();
    vi.mocked(getGame).mockClear();
});

// R25: one reconnect, one read. The stream recovery owns it; the screen must not issue a second one.
it('reads the game once when the browser comes back online', async () => {
    render(
        <GameRoute
            gameId="ReconnectGame0000000001"
            onTitle={() => undefined}
            settingsOpen={false}
            onSettingsClose={() => undefined}
            installAvailable={false}
            onInstall={() => undefined}
        />,
    );
    await waitFor(() => {
        expect(getGame).toHaveBeenCalledTimes(1);
    });
    vi.mocked(getGame).mockClear();

    await act(async () => {
        globalThis.dispatchEvent(new Event('online'));
        await Promise.resolve();
    });

    expect(getGame).toHaveBeenCalledTimes(1);
});
