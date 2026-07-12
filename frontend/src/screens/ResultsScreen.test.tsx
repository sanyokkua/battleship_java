import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import '../i18n';
import { MockGameAdapter } from '../adapters/MockGameAdapter';
import { GameAdapterProvider } from '../adapters/GameAdapterContext';
import { saveSession, savePlayer } from '../services/GameBrowserStorage';
import type { CellDto } from '../logic/ApplicationTypes';
import { ResultsScreen } from './ResultsScreen';

/**
 * Deterministic non-overlapping placement, same pattern as
 * MockGameAdapter.test.ts's placeAllShips helper: two ships per row
 * (columns 0 and 5), rows stepped by 2 so no two rows are adjacent —
 * guarantees no moat collisions for both editions (max 10 ships, max
 * size 5, 10x10 board).
 */
async function placeAllShips(adapter: MockGameAdapter, sessionId: string, playerId: string): Promise<void> {
  const prep = await adapter.getPreparationState(sessionId, playerId);
  let row = 0;
  let column = 0;
  for (const ship of prep.ships) {
    await adapter.addShip(sessionId, playerId, ship.shipId, { row, column }, 'HORIZONTAL');
    if (column === 0) {
      column = 5;
    } else {
      column = 0;
      row += 2;
    }
  }
}

function allShipCoordinates(field: CellDto[][]): { row: number; col: number }[] {
  const coords: { row: number; col: number }[] = [];
  for (const row of field) {
    for (const cell of row) {
      if (cell.ship) {
        coords.push({ row: cell.row, col: cell.col });
      }
    }
  }
  return coords;
}

/**
 * Runs a full session to a finished/hasWinner state: create session, create
 * 2 players (mock auto-advances WAITING_FOR_PLAYERS -> PREPARATION), both
 * place all ships and ready up (mock auto-advances to IN_GAME), then the
 * designated winner shoots every one of the loser's ship cells until the
 * fleet is fully sunk (mock sets hasWinner/winnerPlayerName and stage
 * FINISHED once the target's aliveShips hits 0).
 */
async function setUpFinishedGame(winnerGoesFirst: boolean) {
  const adapter = new MockGameAdapter();
  const sessionId = await adapter.createSession('UKRAINIAN');
  const p1 = await adapter.createPlayer(sessionId, 'Alice');
  const p2 = await adapter.createPlayer(sessionId, 'Bob');

  await placeAllShips(adapter, sessionId, p1.playerId);
  await placeAllShips(adapter, sessionId, p2.playerId);
  await adapter.setReady(sessionId, p1.playerId);
  await adapter.setReady(sessionId, p2.playerId);
  // MockGameAdapter.setReady sets activePlayerId to players[0] (p1) once both are ready.

  const winner = winnerGoesFirst ? p1 : p2;
  const loser = winnerGoesFirst ? p2 : p1;

  // If the intended winner isn't first to move, burn the first (non-winner) turn with a
  // deliberate miss at a cell with no ship, which hands the turn to the winner (turn only
  // switches on a MISS in MockGameAdapter).
  if (!winnerGoesFirst) {
    const firstMoverPrep = await adapter.getPreparationState(sessionId, p1.playerId);
    void firstMoverPrep; // not needed further; p1 always moves first regardless of winner
    await adapter.shoot(sessionId, p1.playerId, { row: 9, column: 9 }); // guaranteed empty per placement layout
  }

  const loserField = (await adapter.getGameState(sessionId, loser.playerId)).playerField;
  const loserShipCells = allShipCoordinates(loserField);

  for (const cell of loserShipCells) {
    await adapter.shoot(sessionId, winner.playerId, { row: cell.row, column: cell.col });
    // Hits grant another turn in MockGameAdapter, so winner keeps shooting uninterrupted.
  }

  return { adapter, sessionId, alice: p1, bob: p2, winnerId: winner.playerId };
}

function renderResultsScreen(adapter: MockGameAdapter) {
  return render(
    <GameAdapterProvider adapter={adapter}>
      <MemoryRouter initialEntries={['/game/results']}>
        <Routes>
          <Route path="/game/results" element={<ResultsScreen />} />
          <Route path="/" element={<div>Home route</div>} />
        </Routes>
      </MemoryRouter>
    </GameAdapterProvider>,
  );
}

describe('ResultsScreen', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('renders the win hero when the viewing player is the winner', async () => {
    const { adapter, sessionId, alice } = await setUpFinishedGame(true);
    saveSession(sessionId);
    savePlayer(alice);

    renderResultsScreen(adapter);

    expect(await screen.findByText('Victory!')).toBeInTheDocument();
    expect(screen.getByText('Alice sank the entire enemy fleet.')).toBeInTheDocument();
  });

  it('renders the lose hero when the viewing player is not the winner', async () => {
    const { adapter, sessionId, bob } = await setUpFinishedGame(true);
    saveSession(sessionId);
    savePlayer(bob);

    renderResultsScreen(adapter);

    expect(await screen.findByText('Defeat')).toBeInTheDocument();
    // winnerSubtitle is reused for both outcomes (see ResultsScreen.tsx doc comment) — winner
    // is still Alice, regardless of whose screen we're viewing.
    expect(screen.getByText('Alice sank the entire enemy fleet.')).toBeInTheDocument();
  });

  it('shows only the Ships-sunk stat, and never Hits or Time', async () => {
    const { adapter, sessionId, alice } = await setUpFinishedGame(true);
    saveSession(sessionId);
    savePlayer(alice);

    renderResultsScreen(adapter);

    expect(await screen.findByText('Ships sunk')).toBeInTheDocument();
    expect(screen.queryByText(/hits/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/time/i)).not.toBeInTheDocument();
  });

  it('renders the ships-sunk count derived from the fixed 10-ship total minus opponent alive ships', async () => {
    const { adapter, sessionId, alice } = await setUpFinishedGame(true);
    saveSession(sessionId);
    savePlayer(alice);

    renderResultsScreen(adapter);

    // Winner fully sank all 10 of the opponent's ships -> 10 - 0 = 10.
    await waitFor(() => expect(screen.getByText('Ships sunk')).toBeInTheDocument());
    const statBlock = screen.getByText('Ships sunk').closest('.b');
    expect(statBlock).not.toBeNull();
    expect(statBlock!.querySelector('.n')?.textContent).toBe('10');
  });

  it('renders both boards read-only: clicking a cell does not throw or call any adapter method', async () => {
    const { adapter, sessionId, alice } = await setUpFinishedGame(true);
    saveSession(sessionId);
    savePlayer(alice);

    const shootSpy = vi.spyOn(adapter, 'shoot');
    const user = userEvent.setup();

    renderResultsScreen(adapter);

    await screen.findByText('Victory!');

    const boards = document.querySelectorAll('.board.readonly');
    expect(boards.length).toBe(2);

    const firstCell = boards[0].querySelector('.cell');
    expect(firstCell).not.toBeNull();
    await user.click(firstCell as Element);

    expect(shootSpy).not.toHaveBeenCalled();
  });

  it('clicking "Return to main menu" clears storage and navigates to /', async () => {
    const { adapter, sessionId, alice } = await setUpFinishedGame(true);
    saveSession(sessionId);
    savePlayer(alice);

    const user = userEvent.setup();
    renderResultsScreen(adapter);

    await screen.findByText('Victory!');
    expect(localStorage.getItem('session_str')).toBe(sessionId);

    await user.click(screen.getByRole('button', { name: 'Return to main menu' }));

    expect(await screen.findByText('Home route')).toBeInTheDocument();
    expect(localStorage.getItem('session_str')).toBeNull();
    expect(localStorage.getItem('player_obj')).toBeNull();
    expect(localStorage.getItem('gameStage_str')).toBeNull();
  });
});
