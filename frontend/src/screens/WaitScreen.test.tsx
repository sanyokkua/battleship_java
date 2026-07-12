import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import '../i18n';
import { MockGameAdapter } from '../adapters/MockGameAdapter';
import { GameAdapterProvider } from '../adapters/GameAdapterContext';
import { ToastProvider } from '../widgets/feedback/ToastContext';
import { ToastStack } from '../widgets/feedback/ToastStack';
import { saveSession, savePlayer } from '../services/GameBrowserStorage';
import { WaitScreen } from './WaitScreen';

/**
 * Fake-timers gotcha (see useWaitRoom.test.tsx): MockGameAdapter's methods are
 * async, so with fake timers, plain vi.advanceTimersByTime does not flush the
 * microtasks backing those awaits. Use shouldAdvanceTime + advanceTimersByTimeAsync
 * wrapped in act() to avoid flakiness.
 */

function renderWaitScreen(adapter: MockGameAdapter) {
  return render(
    <GameAdapterProvider adapter={adapter}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/game/wait']}>
          <Routes>
            <Route path="/game/wait" element={<WaitScreen />} />
            <Route path="/game/preparation" element={<div>Preparation route</div>} />
          </Routes>
        </MemoryRouter>
        <ToastStack />
      </ToastProvider>
    </GameAdapterProvider>,
  );
}

describe('WaitScreen', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      configurable: true,
    });
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

    // fireEvent (not userEvent) here deliberately: userEvent.click's pointer/focus
    // event simulation in this jsdom version clobbers the navigator.clipboard stub
    // defined above (observed empirically), which fireEvent's plain click does not.
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Copy' }));
    });

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(sessionId);
    await waitFor(() => expect(screen.getByText('Game ID copied to clipboard.')).toBeInTheDocument());
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
