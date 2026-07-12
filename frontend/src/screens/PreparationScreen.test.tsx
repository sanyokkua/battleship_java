import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import '../i18n';
import { MockGameAdapter } from '../adapters/MockGameAdapter';
import { GameAdapterProvider } from '../adapters/GameAdapterContext';
import { GameAdapterError } from '../adapters/AdapterErrors';
import { ToastProvider } from '../widgets/feedback/ToastContext';
import { ToastStack } from '../widgets/feedback/ToastStack';
import { saveSession, savePlayer, clearGameData } from '../services/GameBrowserStorage';
import { PreparationScreen, validatePlacement } from './PreparationScreen';

function renderPrepScreen(adapter: MockGameAdapter) {
  return render(
    <GameAdapterProvider adapter={adapter}>
      <ToastProvider>
        <ToastStack />
        <MemoryRouter initialEntries={['/game/preparation']}>
          <Routes>
            <Route path="/game/preparation" element={<PreparationScreen />} />
            <Route path="/game/gameplay" element={<div>Gameplay route</div>} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </GameAdapterProvider>,
  );
}

async function setUpSeededSession(edition = 'UKRAINIAN') {
  const adapter = new MockGameAdapter();
  const sessionId = await adapter.createSession(edition);
  const p1 = await adapter.createPlayer(sessionId, 'Alice');
  await adapter.createPlayer(sessionId, 'Bob');
  saveSession(sessionId);
  savePlayer(p1);
  return { adapter, sessionId, playerId: p1.playerId };
}

async function waitForBoardToLoad() {
  await waitFor(() => {
    expect(screen.getAllByRole('button', { name: /^[A-J](?:10|[1-9]),/ }).length).toBeGreaterThan(0);
  });
}

// UKRAINIAN edition has 4 Patrol Boats (size 1) — grab the first unplaced tray entry.
function firstPatrolBoatButton() {
  return screen.getAllByRole('button', { name: /Patrol Boat/ })[0];
}

describe('PreparationScreen', () => {
  afterEach(() => {
    clearGameData();
  });

  it('renders the fleet tray with correct "X of Y placed" progress', async () => {
    const { adapter } = await setUpSeededSession();
    renderPrepScreen(adapter);

    await waitForBoardToLoad();

    // UKRAINIAN edition: 10 ships total, none placed yet.
    expect(await screen.findByText('0 of 10 ships placed')).toBeInTheDocument();
  });

  it('selects a ship, sets direction, then a cell tap calls addShip with the right args', async () => {
    const user = userEvent.setup();
    const { adapter, sessionId, playerId } = await setUpSeededSession();
    const addShipSpy = vi.spyOn(adapter, 'addShip');

    renderPrepScreen(adapter);
    await waitForBoardToLoad();

    await user.click(firstPatrolBoatButton());

    // Switch to vertical direction.
    await user.click(screen.getByRole('button', { name: /Vertical/ }));

    // Tap cell A1 (row 0, col 0).
    await user.click(screen.getByRole('button', { name: /^A1,/ }));

    await waitFor(() => {
      expect(addShipSpy).toHaveBeenCalledWith(sessionId, playerId, expect.any(String), { row: 0, column: 0 }, 'VERTICAL');
    });

    expect(await screen.findByText('1 of 10 ships placed')).toBeInTheDocument();
  });

  it('removes a placed ship via the board-tap path', async () => {
    const user = userEvent.setup();
    const { adapter } = await setUpSeededSession();
    const removeShipSpy = vi.spyOn(adapter, 'removeShip');

    renderPrepScreen(adapter);
    await waitForBoardToLoad();

    await user.click(firstPatrolBoatButton());
    await user.click(screen.getByRole('button', { name: /^A1,/ }));

    await waitFor(() => expect(screen.getByText('1 of 10 ships placed')).toBeInTheDocument());

    // Tap the now-occupied A1 cell again — should remove instead of place.
    await user.click(screen.getByRole('button', { name: /^A1,/ }));

    await waitFor(() => expect(removeShipSpy).toHaveBeenCalledWith(expect.any(String), expect.any(String), { row: 0, column: 0 }));
    expect(await screen.findByText('0 of 10 ships placed')).toBeInTheDocument();
  });

  it('removes a placed ship via the tray remove (✕) button', async () => {
    const user = userEvent.setup();
    const { adapter } = await setUpSeededSession();
    const removeShipSpy = vi.spyOn(adapter, 'removeShip');

    renderPrepScreen(adapter);
    await waitForBoardToLoad();

    await user.click(firstPatrolBoatButton());
    await user.click(screen.getByRole('button', { name: /^A1,/ }));
    await waitFor(() => expect(screen.getByText('1 of 10 ships placed')).toBeInTheDocument());

    const removeBtn = await screen.findByRole('button', { name: /Patrol Boat ✕/ });
    await user.click(removeBtn);

    await waitFor(() => expect(removeShipSpy).toHaveBeenCalledWith(expect.any(String), expect.any(String), { row: 0, column: 0 }));
    expect(await screen.findByText('0 of 10 ships placed')).toBeInTheDocument();
  });

  it('renders blocked (no-go moat) cells once a ship is placed', async () => {
    const user = userEvent.setup();
    const { adapter } = await setUpSeededSession();

    renderPrepScreen(adapter);
    await waitForBoardToLoad();

    await user.click(firstPatrolBoatButton());
    // Place at B2 (row 1, col 1) so it has neighbours on every side to become blocked.
    await user.click(screen.getByRole('button', { name: /^B2,/ }));

    // A1 is a diagonal neighbour of B2 and should now be rendered as blocked — blocked
    // cells render as a non-interactive <div> (not a <button>), so assert via label text.
    await waitFor(() => {
      expect(screen.getByLabelText(/^A1, blocked/)).toBeInTheDocument();
    });
  });

  it('Ready button is disabled until all ships are placed, and calls markReady once enabled', async () => {
    const user = userEvent.setup();
    const { adapter, sessionId, playerId } = await setUpSeededSession();
    const setReadySpy = vi.spyOn(adapter, 'setReady');

    renderPrepScreen(adapter);
    await waitForBoardToLoad();

    expect(screen.getByRole('button', { name: /Ready to go!/ })).toBeDisabled();

    // Place every ship through the UI (tray select -> board tap). VERTICAL placement,
    // round-robin across 5 columns (A,C,E,G,I — every other column, so neighbouring ships
    // never touch diagonally), stacked with a 1-row gap — mirrors usePreparation.test.tsx's
    // own non-colliding layout helper, adapted to drive placement via the UI.
    await user.click(screen.getByRole('button', { name: /Vertical/ }));
    const columnLetters = ['A', 'C', 'E', 'G', 'I'];
    const nextRowByColumn = new Map<string, number>(columnLetters.map((c) => [c, 0]));
    for (let i = 0; i < 10; i++) {
      // Selectable (unplaced) tray entries render as a plain `<button class="ship-item">`
      // with no "remove-btn" descendant; the placed Battleship's remove (✕) button also
      // matches a loose name-based query, so select by class instead to avoid it.
      const unplacedButtons = document.querySelectorAll<HTMLButtonElement>('button.ship-item');
      const target = unplacedButtons[0];
      await user.click(target);
      const sizeMatch = /(\d+) cells?/.exec(target.textContent ?? '');
      const size = sizeMatch ? Number(sizeMatch[1]) : 1;
      const column = columnLetters[i % columnLetters.length];
      const row = nextRowByColumn.get(column)!;
      const cellButton = screen.getByRole('button', { name: new RegExp(`^${column}${row + 1},`) });
      await user.click(cellButton);
      await waitFor(() => expect(screen.getByText(`${i + 1} of 10 ships placed`)).toBeInTheDocument());
      nextRowByColumn.set(column, row + size + 1);
    }

    await waitFor(() => expect(screen.getByRole('button', { name: /Ready to go!/ })).toBeEnabled());

    await user.click(screen.getByRole('button', { name: /Ready to go!/ }));
    await waitFor(() => expect(setReadySpy).toHaveBeenCalledWith(sessionId, playerId));
  });

  it('auto-advances to the next ship after a successful placement, so a second board tap places without reselecting the tray', async () => {
    const user = userEvent.setup();
    const { adapter, sessionId, playerId } = await setUpSeededSession();
    const addShipSpy = vi.spyOn(adapter, 'addShip');

    renderPrepScreen(adapter);
    await waitForBoardToLoad();

    await user.click(firstPatrolBoatButton());
    await user.click(screen.getByRole('button', { name: /^A1,/ }));

    await waitFor(() => expect(addShipSpy).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByText('1 of 10 ships placed')).toBeInTheDocument());

    // No further tray click — auto-advance should already have selected the next
    // ship (UKRAINIAN fleet's remaining largest unplaced ship: the size-4 Battleship,
    // per ShipTray's own descending-by-size sort order). Tap a cell far from A1's moat
    // (row 0, cols 0-1) so a HORIZONTAL size-4 placement there is unambiguously valid.
    await user.click(screen.getByRole('button', { name: /^F1,/ }));

    await waitFor(() => expect(addShipSpy).toHaveBeenCalledTimes(2));
    expect(addShipSpy).toHaveBeenNthCalledWith(2, sessionId, playerId, expect.any(String), { row: 0, column: 5 }, 'HORIZONTAL');
    expect(await screen.findByText('2 of 10 ships placed')).toBeInTheDocument();
  });

  it('does not auto-advance the tray selection when the server rejects a placement', async () => {
    const user = userEvent.setup();
    const { adapter } = await setUpSeededSession();
    vi.spyOn(adapter, 'addShip').mockRejectedValueOnce(
      new GameAdapterError('Unknown shipId', { httpStatus: 400, errorCode: 'SHIP_ID_INVALID', context: 'addShip' }),
    );

    renderPrepScreen(adapter);
    await waitForBoardToLoad();

    const shipButton = firstPatrolBoatButton();
    await user.click(shipButton);
    await user.click(screen.getByRole('button', { name: /^A1,/ }));

    expect(await screen.findByText("That ship isn't valid.")).toBeInTheDocument();

    // The previously-active ship must still be active — no auto-advance on failure.
    expect(shipButton).toHaveClass('active');
  });

  it('shows an error toast for an out-of-bounds client-side pre-check placement', async () => {
    const user = userEvent.setup();
    const { adapter } = await setUpSeededSession();
    const addShipSpy = vi.spyOn(adapter, 'addShip');

    renderPrepScreen(adapter);
    await waitForBoardToLoad();

    // Select the size-4 Battleship (only one in the UKRAINIAN edition).
    const shipButton = await screen.findByRole('button', { name: /Battleship/ });
    await user.click(shipButton);
    // Default direction is HORIZONTAL; placing at J1 (col 9) with size 4 goes off the board.
    await user.click(screen.getByRole('button', { name: /^J1,/ }));

    expect(await screen.findByText("Can't place there")).toBeInTheDocument();
    expect(await screen.findByText('That placement goes off the board.')).toBeInTheDocument();
    expect(addShipSpy).not.toHaveBeenCalled();
  });

  it('validatePlacement flags an occupied cell (unit-level check of the client pre-check helper)', async () => {
    const { adapter, sessionId, playerId } = await setUpSeededSession();
    const prep = await adapter.getPreparationState(sessionId, playerId);
    const ship = prep.ships[0];
    // Place a size-1 ship server-side at (5,5).
    await adapter.addShip(sessionId, playerId, ship.shipId, { row: 5, column: 5 }, 'HORIZONTAL');
    const updated = await adapter.getPreparationState(sessionId, playerId);

    // A size-2 ship placed HORIZONTAL starting at (5,4) covers cells (5,4) [free, in the
    // moat] and (5,5) [occupied by the placed ship] — occupied should win over tooClose.
    const result = validatePlacement(updated.field, { row: 5, column: 4 }, 2, 'HORIZONTAL');
    expect(result).toBe('occupied');
  });

  it('validatePlacement flags a moat (no-go) cell as tooClose (unit-level check of the client pre-check helper)', async () => {
    const { adapter, sessionId, playerId } = await setUpSeededSession();
    const prep = await adapter.getPreparationState(sessionId, playerId);
    const ship = prep.ships[0];
    // Place a size-1 ship at (5,5), which makes its 8 neighbours unavailable (moat).
    await adapter.addShip(sessionId, playerId, ship.shipId, { row: 5, column: 5 }, 'HORIZONTAL');
    const updated = await adapter.getPreparationState(sessionId, playerId);

    // (5,6) is a moat cell (immediate neighbour of the placed ship) but not itself occupied —
    // a size-1 ship placed there should be flagged 'tooClose', not 'occupied'.
    const result = validatePlacement(updated.field, { row: 5, column: 6 }, 1, 'HORIZONTAL');
    expect(result).toBe('tooClose');
  });

  it('the board renders moat cells as non-clickable, so a UI tap on one never reaches placeShip', async () => {
    const user = userEvent.setup();
    const { adapter } = await setUpSeededSession();
    const addShipSpy = vi.spyOn(adapter, 'addShip');

    renderPrepScreen(adapter);
    await waitForBoardToLoad();

    // Place a Patrol Boat (size 1) at E5 (row 4, col 4), creating a moat around it.
    await user.click(firstPatrolBoatButton());
    await user.click(screen.getByRole('button', { name: /^E5,/ }));
    await waitFor(() => expect(screen.getByText('1 of 10 ships placed')).toBeInTheDocument());
    addShipSpy.mockClear();

    // D4 (row 3, col 3) is a diagonal moat neighbour of E5 and is rendered as a non-clickable div.
    const moatCell = screen.getByLabelText(/^D4, blocked/);
    expect(moatCell.tagName).toBe('DIV');

    await user.click(moatCell);
    expect(addShipSpy).not.toHaveBeenCalled();
  });

  it('surfaces a generic error toast when the server rejects a placement that passed the client pre-check', async () => {
    const user = userEvent.setup();
    const { adapter } = await setUpSeededSession();
    vi.spyOn(adapter, 'addShip').mockRejectedValueOnce(
      new GameAdapterError('Unknown shipId', { httpStatus: 400, errorCode: 'SHIP_ID_INVALID', context: 'addShip' }),
    );

    renderPrepScreen(adapter);
    await waitForBoardToLoad();

    await user.click(firstPatrolBoatButton());
    await user.click(screen.getByRole('button', { name: /^A1,/ }));

    expect(await screen.findByText("That ship isn't valid.")).toBeInTheDocument();
  });

  it('does not render any auto-place control anywhere in the DOM', async () => {
    const { adapter } = await setUpSeededSession();
    renderPrepScreen(adapter);
    await waitForBoardToLoad();

    expect(screen.queryByText(/auto.?place/i)).not.toBeInTheDocument();
  });
});
