import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {act, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import '../i18n';
import {MockGameAdapter} from '../adapters/MockGameAdapter';
import {GameAdapterProvider} from '../adapters/GameAdapterContext';
import {ToastProvider} from '../widgets/feedback/ToastContext';
import {ToastStack} from '../widgets/feedback/ToastStack';
import {savePlayer, saveSession} from '../services/GameBrowserStorage';
import {WaitScreen} from './WaitScreen';

/**
 * Fake-timers gotcha (see useWaitRoom.test.tsx): MockGameAdapter's methods are
 * async, so with fake timers, plain vi.advanceTimersByTime does not flush the
 * microtasks backing those awaits. Use shouldAdvanceTime + advanceTimersByTimeAsync
 * wrapped in act() to avoid flakiness.
 */

// WaitScreen copies via copy-to-clipboard (not navigator.clipboard directly) so the
// Copy/Copy-link buttons keep working on plain-HTTP LAN addresses, where jsdom (like
// a real non-secure-context browser) has no usable navigator.clipboard/execCommand.
// Mock the module so we can assert on its calls without depending on jsdom internals.
const copyMock = vi.fn().mockResolvedValue(true);
vi.mock('copy-to-clipboard', () => ({default: (text: string) => copyMock(text)}));

function renderWaitScreen(adapter: MockGameAdapter) {
    return render(
        <GameAdapterProvider adapter={adapter}>
            <ToastProvider>
                <MemoryRouter initialEntries={['/game/wait']}>
                    <Routes>
                        <Route path="/game/wait" element={<WaitScreen/>}/>
                        <Route path="/game/preparation" element={<div>Preparation route</div>}/>
                    </Routes>
                </MemoryRouter>
                <ToastStack/>
            </ToastProvider>
        </GameAdapterProvider>,
    );
}

describe('WaitScreen', () => {
    beforeEach(() => {
        localStorage.clear();
        vi.useFakeTimers({shouldAdvanceTime: true});
        copyMock.mockReset();
        copyMock.mockResolvedValue(true);
    });

    afterEach(() => {
        vi.useRealTimers();
        localStorage.clear();
    });

    it('shows the "wait" step as active and greets the player by name', async () => {
        const adapter = new MockGameAdapter();
        const sessionId = await adapter.createSession('UKRAINIAN');
        const player = await adapter.createPlayer(sessionId, 'Alice');
        saveSession(sessionId);
        savePlayer(player);

        renderWaitScreen(adapter);

        await waitFor(() => expect(screen.getByText('Hello, Alice!')).toBeInTheDocument());

        const activeStep = screen.getByText('Waiting').closest('.st');
        expect(activeStep).toHaveClass('active');
        expect(screen.getByText(sessionId)).toBeInTheDocument();
    });

    it('copies the session id and shows the "copied" toast when Copy is clicked', async () => {
        const adapter = new MockGameAdapter();
        const sessionId = await adapter.createSession('UKRAINIAN');
        const player = await adapter.createPlayer(sessionId, 'Alice');
        saveSession(sessionId);
        savePlayer(player);

        renderWaitScreen(adapter);

        await waitFor(() => expect(screen.getByText('Hello, Alice!')).toBeInTheDocument());

        // fireEvent (not userEvent) here deliberately, matching this file's existing
        // convention for the clipboard buttons.
        await act(async () => {
            fireEvent.click(screen.getByRole('button', {name: 'Copy'}));
        });

        expect(copyMock).toHaveBeenCalledWith(sessionId);
        await waitFor(() => expect(screen.getByText('Game ID copied to clipboard.')).toBeInTheDocument());
    });

    it('copies a shareable join link and shows the "link copied" toast when Copy link is clicked', async () => {
        const adapter = new MockGameAdapter();
        const sessionId = await adapter.createSession('UKRAINIAN');
        const player = await adapter.createPlayer(sessionId, 'Alice');
        saveSession(sessionId);
        savePlayer(player);

        renderWaitScreen(adapter);

        await waitFor(() => expect(screen.getByText('Hello, Alice!')).toBeInTheDocument());

        await act(async () => {
            fireEvent.click(screen.getByRole('button', {name: 'Copy link'}));
        });

        // jsdom's default test origin, per this project's vitest.config.ts (jsdom
        // environment, no custom testEnvironmentOptions.url), is http://localhost:3000.
        expect(copyMock).toHaveBeenCalledWith(`http://localhost:3000/join?id=${sessionId}`);
        await waitFor(() => expect(screen.getByText('Join link copied to clipboard.')).toBeInTheDocument());
    });

    it('shows no toast when the underlying copy fails', async () => {
        copyMock.mockResolvedValue(false);

        const adapter = new MockGameAdapter();
        const sessionId = await adapter.createSession('UKRAINIAN');
        const player = await adapter.createPlayer(sessionId, 'Alice');
        saveSession(sessionId);
        savePlayer(player);

        renderWaitScreen(adapter);

        await waitFor(() => expect(screen.getByText('Hello, Alice!')).toBeInTheDocument());

        await act(async () => {
            fireEvent.click(screen.getByRole('button', {name: 'Copy'}));
        });

        expect(copyMock).toHaveBeenCalledWith(sessionId);
        expect(screen.queryByText('Game ID copied to clipboard.')).not.toBeInTheDocument();
    });

    it('shows a refresh button that re-fetches the session snapshot when clicked', async () => {
        const adapter = new MockGameAdapter();
        const sessionId = await adapter.createSession('UKRAINIAN');
        const player = await adapter.createPlayer(sessionId, 'Alice');
        saveSession(sessionId);
        savePlayer(player);

        renderWaitScreen(adapter);

        await waitFor(() => expect(screen.getByText('Hello, Alice!')).toBeInTheDocument());

        const getStageSpy = vi.spyOn(adapter, 'getStage');
        const getOpponentSpy = vi.spyOn(adapter, 'getOpponent');

        await act(async () => {
            fireEvent.click(screen.getByRole('button', {name: '⟳ Refresh'}));
        });

        expect(getStageSpy).toHaveBeenCalledWith(sessionId);
        expect(getOpponentSpy).toHaveBeenCalledWith(sessionId, player.playerId);
    });

    it('navigates to /game/preparation once the opponent joins and the stage advances', async () => {
        const adapter = new MockGameAdapter();
        const sessionId = await adapter.createSession('UKRAINIAN');
        const player = await adapter.createPlayer(sessionId, 'Alice');
        saveSession(sessionId);
        savePlayer(player);

        renderWaitScreen(adapter);

        await waitFor(() => expect(screen.getByText('Hello, Alice!')).toBeInTheDocument());

        // MockGameAdapter.createPlayer flips the session's stage from WAITING_FOR_PLAYERS
        // to PREPARATION automatically once the second player joins (session.players.length >= 2).
        await act(async () => {
            await adapter.createPlayer(sessionId, 'Bob');
        });

        await act(async () => {
            await vi.advanceTimersByTimeAsync(3100);
        });

        await waitFor(() => expect(screen.getByText('Preparation route')).toBeInTheDocument());
        expect(localStorage.getItem('gameStage_str')).toBe('PREPARATION');
    });
});
