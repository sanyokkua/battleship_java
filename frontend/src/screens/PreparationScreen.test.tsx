import {afterEach, describe, expect, it, vi} from 'vitest';
import {act, fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import '../i18n';
import {MockGameAdapter} from '../adapters/MockGameAdapter';
import {GameAdapterProvider} from '../adapters/GameAdapterContext';
import {GameAdapterError} from '../adapters/AdapterErrors';
import {ToastProvider} from '../widgets/feedback/ToastContext';
import {ToastStack} from '../widgets/feedback/ToastStack';
import {clearGameData, savePlayer, saveSession} from '../services/GameBrowserStorage';
import {PreparationScreen} from './PreparationScreen';

function renderPrepScreen(adapter: MockGameAdapter) {
    return render(
        <GameAdapterProvider adapter={adapter}>
            <ToastProvider>
                <ToastStack/>
                <MemoryRouter initialEntries={['/game/preparation']}>
                    <Routes>
                        <Route path="/game/preparation" element={<PreparationScreen/>}/>
                        <Route path="/game/gameplay" element={<div>Gameplay route</div>}/>
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
    return {adapter, sessionId, playerId: p1.playerId};
}

async function waitForBoardToLoad() {
    await waitFor(() => {
        expect(screen.getAllByRole('button', {name: /^[A-J](?:10|[1-9]),/}).length).toBeGreaterThan(0);
    });
}

/**
 * Drives the tap-empty-cell placement popup end to end: tap `cellPattern`, pick the ship
 * matching `shipNamePattern` from the ship-picking step, then (only if the ship's size is
 * greater than 1 and a direction step actually appears) pick `direction`. Waits for the
 * popup to close, confirming the placement completed.
 */
async function placeShipViaPopup(
    user: ReturnType<typeof userEvent.setup>,
    cellPattern: RegExp,
    shipNamePattern: RegExp,
    direction: 'HORIZONTAL' | 'VERTICAL' = 'HORIZONTAL',
) {
    await user.click(screen.getByRole('button', {name: cellPattern}));
    const popup = await screen.findByRole('dialog');
    await user.click(within(popup).getByRole('button', {name: shipNamePattern}));

    const directionLabel = direction === 'VERTICAL' ? 'Vertical' : 'Horizontal';
    const directionButton = within(popup).queryByRole('button', {name: directionLabel});
    if (directionButton) {
        await user.click(directionButton);
    }

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
}

describe('PreparationScreen', () => {
    afterEach(() => {
        clearGameData();
    });

    it('renders the "X of Y placed" progress', async () => {
        const {adapter} = await setUpSeededSession();
        renderPrepScreen(adapter);

        await waitForBoardToLoad();

        // UKRAINIAN edition: 10 ships total, none placed yet.
        expect(await screen.findByText('0 of 10 ships placed')).toBeInTheDocument();
    });

    it('picks a ship, picks a direction in the popup, then confirms calls addShip with the right args', async () => {
        const user = userEvent.setup();
        const {adapter, sessionId, playerId} = await setUpSeededSession();
        const addShipSpy = vi.spyOn(adapter, 'addShip');

        renderPrepScreen(adapter);
        await waitForBoardToLoad();

        // Submarine (size 2) — a 1-cell ship would confirm immediately with no direction
        // step, so it can't exercise this path.
        await placeShipViaPopup(user, /^A1,/, /Submarine/, 'VERTICAL');

        await waitFor(() => {
            expect(addShipSpy).toHaveBeenCalledWith(sessionId, playerId, expect.any(String), {
                row: 0,
                column: 0
            }, 'VERTICAL');
        });

        expect(await screen.findByText('1 of 10 ships placed')).toBeInTheDocument();
    });

    it('tapping a placed ship opens the rotate/remove action popup instead of removing instantly', async () => {
        const user = userEvent.setup();
        const {adapter} = await setUpSeededSession();
        const removeShipSpy = vi.spyOn(adapter, 'removeShip');

        renderPrepScreen(adapter);
        await waitForBoardToLoad();

        await placeShipViaPopup(user, /^A1,/, /Patrol Boat/);
        await waitFor(() => expect(screen.getByText('1 of 10 ships placed')).toBeInTheDocument());

        // Tap the now-occupied A1 cell again — should open the popup, not remove instantly.
        await user.click(screen.getByRole('button', {name: /^A1,/}));

        expect(await screen.findByRole('dialog')).toBeInTheDocument();
        expect(screen.getByRole('heading', {name: 'Patrol Boat'})).toBeInTheDocument();
        expect(removeShipSpy).not.toHaveBeenCalled();
    });

    it('removes a placed ship via the action popup\'s Remove button', async () => {
        const user = userEvent.setup();
        const {adapter} = await setUpSeededSession();
        const removeShipSpy = vi.spyOn(adapter, 'removeShip');

        renderPrepScreen(adapter);
        await waitForBoardToLoad();

        await placeShipViaPopup(user, /^A1,/, /Patrol Boat/);
        await waitFor(() => expect(screen.getByText('1 of 10 ships placed')).toBeInTheDocument());

        await user.click(screen.getByRole('button', {name: /^A1,/}));
        const popup = await screen.findByRole('dialog');
        await user.click(within(popup).getByRole('button', {name: 'Remove'}));

        await waitFor(() => expect(removeShipSpy).toHaveBeenCalledWith(expect.any(String), expect.any(String), {
            row: 0,
            column: 0
        }));
        expect(await screen.findByText('0 of 10 ships placed')).toBeInTheDocument();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('rotates a placed ship via the action popup\'s Rotate button (remove then re-add in the new direction)', async () => {
        const user = userEvent.setup();
        const {adapter, sessionId, playerId} = await setUpSeededSession();
        const removeShipSpy = vi.spyOn(adapter, 'removeShip');
        const addShipSpy = vi.spyOn(adapter, 'addShip');

        renderPrepScreen(adapter);
        await waitForBoardToLoad();

        // Place the Battleship (size 4) HORIZONTAL at E5 — far from the board edge in every
        // direction, so rotating it VERTICAL in place is unambiguously valid.
        await placeShipViaPopup(user, /^E5,/, /Battleship/, 'HORIZONTAL');
        await waitFor(() => expect(screen.getByText('1 of 10 ships placed')).toBeInTheDocument());
        addShipSpy.mockClear();

        await user.click(screen.getByRole('button', {name: /^E5,/}));
        const popup = await screen.findByRole('dialog');
        await user.click(within(popup).getByRole('button', {name: 'Rotate'}));

        await waitFor(() => expect(removeShipSpy).toHaveBeenCalledWith(sessionId, playerId, {row: 4, column: 4}));
        await waitFor(() => expect(addShipSpy).toHaveBeenCalledWith(sessionId, playerId, expect.any(String), {
            row: 4,
            column: 4
        }, 'VERTICAL'));
        // Order matters: the rotate must remove before re-adding.
        expect(removeShipSpy.mock.invocationCallOrder[0]).toBeLessThan(addShipSpy.mock.invocationCallOrder[0]);
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    });

    it('hides the Rotate button when the ship has no room to rotate in place', async () => {
        const user = userEvent.setup();
        const {adapter} = await setUpSeededSession();

        renderPrepScreen(adapter);
        await waitForBoardToLoad();

        // Battleship (size 4) placed along the very bottom row: only HORIZONTAL fits at
        // A10, so the popup's direction step offers just that one option; rotating it
        // VERTICAL in place would go off the board.
        await placeShipViaPopup(user, /^A10,/, /Battleship/, 'HORIZONTAL');
        await waitFor(() => expect(screen.getByText('1 of 10 ships placed')).toBeInTheDocument());

        await user.click(screen.getByRole('button', {name: /^A10,/}));
        const popup = await screen.findByRole('dialog');

        expect(within(popup).queryByRole('button', {name: 'Rotate'})).not.toBeInTheDocument();
        expect(within(popup).getByRole('button', {name: 'Remove'})).toBeInTheDocument();
    });

    it('surfaces a specific "rotate failed" message when remove succeeds but re-add fails', async () => {
        const user = userEvent.setup();
        const {adapter, sessionId, playerId} = await setUpSeededSession();

        // Seed the placement directly via the adapter (before spying), so the mocked
        // rejection below only intercepts the rotate's re-add call, not this initial one.
        const prep = await adapter.getPreparationState(sessionId, playerId);
        const battleship = prep.ships.find((s) => s.shipSize === 4)!;
        await adapter.addShip(sessionId, playerId, battleship.shipId, {row: 4, column: 4}, 'HORIZONTAL');

        vi.spyOn(adapter, 'addShip').mockRejectedValueOnce(
            new GameAdapterError('Unknown shipId', {httpStatus: 400, errorCode: 'SHIP_ID_INVALID', context: 'addShip'}),
        );

        renderPrepScreen(adapter);
        await waitForBoardToLoad();
        await waitFor(() => expect(screen.getByText('1 of 10 ships placed')).toBeInTheDocument());

        await user.click(screen.getByRole('button', {name: /^E5,/}));
        const popup = await screen.findByRole('dialog');
        await user.click(within(popup).getByRole('button', {name: 'Rotate'}));

        expect(await screen.findByText('Rotate failed')).toBeInTheDocument();
        expect(screen.getByText('Ship removed — place it again.')).toBeInTheDocument();
        // The mocked failure means the ship is now actually gone server-side (remove succeeded).
        expect(await screen.findByText('0 of 10 ships placed')).toBeInTheDocument();
    });

    it('renders blocked (no-go moat) cells once a ship is placed', async () => {
        const user = userEvent.setup();
        const {adapter} = await setUpSeededSession();

        renderPrepScreen(adapter);
        await waitForBoardToLoad();

        // Place at B2 (row 1, col 1) so it has neighbours on every side to become blocked.
        await placeShipViaPopup(user, /^B2,/, /Patrol Boat/);

        // A1 is a diagonal neighbour of B2 and should now be rendered as blocked — blocked
        // cells render as a non-interactive <div> (not a <button>), so assert via label text.
        await waitFor(() => {
            expect(screen.getByLabelText(/^A1, blocked/)).toBeInTheDocument();
        });
    });

    it('Ready button is disabled until all ships are placed, and calls markReady once enabled', async () => {
        const user = userEvent.setup();
        const {adapter, sessionId, playerId} = await setUpSeededSession();
        const setReadySpy = vi.spyOn(adapter, 'setReady');

        renderPrepScreen(adapter);
        await waitForBoardToLoad();

        expect(screen.getByRole('button', {name: /Ready to go!/})).toBeDisabled();

        // Place every ship through the popup, VERTICAL, round-robin across 5 columns
        // (A,C,E,G,I — every other column, so neighbouring ships never touch diagonally),
        // stacked with a 1-row gap. UKRAINIAN edition: 1 Battleship(4), 2 Destroyers(3),
        // 3 Submarines(2), 4 Patrol Boats(1) — largest-first order isn't required by the
        // popup flow (it selects by tapped cell, not tray order), but this order still
        // guarantees the non-colliding column/row layout below stays within bounds.
        const SHIP_NAME_BY_SIZE: Record<number, RegExp> = {
            4: /Battleship/,
            3: /Destroyer/,
            2: /Submarine/,
            1: /Patrol Boat/,
        };
        const sizesInPlacementOrder = [4, 3, 3, 2, 2, 2, 1, 1, 1, 1];
        const columnLetters = ['A', 'C', 'E', 'G', 'I'];
        const nextRowByColumn = new Map<string, number>(columnLetters.map((c) => [c, 0]));

        for (let i = 0; i < sizesInPlacementOrder.length; i++) {
            const size = sizesInPlacementOrder[i];
            const column = columnLetters[i % columnLetters.length];
            const row = nextRowByColumn.get(column)!;
            await placeShipViaPopup(user, new RegExp(`^${column}${row + 1},`), SHIP_NAME_BY_SIZE[size], 'VERTICAL');
            await waitFor(() => expect(screen.getByText(`${i + 1} of 10 ships placed`)).toBeInTheDocument());
            nextRowByColumn.set(column, row + size + 1);
        }

        await waitFor(() => expect(screen.getByRole('button', {name: /Ready to go!/})).toBeEnabled());

        await user.click(screen.getByRole('button', {name: /Ready to go!/}));
        await waitFor(() => expect(setReadySpy).toHaveBeenCalledWith(sessionId, playerId));
    });

    it('the board renders moat cells as non-clickable, so a UI tap on one never reaches placeShip', async () => {
        const user = userEvent.setup();
        const {adapter} = await setUpSeededSession();
        const addShipSpy = vi.spyOn(adapter, 'addShip');

        renderPrepScreen(adapter);
        await waitForBoardToLoad();

        // Place a Patrol Boat (size 1) at E5 (row 4, col 4), creating a moat around it.
        await placeShipViaPopup(user, /^E5,/, /Patrol Boat/);
        await waitFor(() => expect(screen.getByText('1 of 10 ships placed')).toBeInTheDocument());
        addShipSpy.mockClear();

        // D4 (row 3, col 3) is a diagonal moat neighbour of E5 and is rendered as a non-clickable div.
        const moatCell = screen.getByLabelText(/^D4, blocked/);
        expect(moatCell.tagName).toBe('DIV');

        await user.click(moatCell);
        expect(addShipSpy).not.toHaveBeenCalled();
    });

    it('surfaces a generic error toast when the server rejects a placement', async () => {
        const user = userEvent.setup();
        const {adapter} = await setUpSeededSession();
        vi.spyOn(adapter, 'addShip').mockRejectedValueOnce(
            new GameAdapterError('Unknown shipId', {httpStatus: 400, errorCode: 'SHIP_ID_INVALID', context: 'addShip'}),
        );

        renderPrepScreen(adapter);
        await waitForBoardToLoad();

        await user.click(screen.getByRole('button', {name: /^A1,/}));
        const popup = await screen.findByRole('dialog');
        await user.click(within(popup).getByRole('button', {name: /Patrol Boat/}));

        expect(await screen.findByText("That ship isn't valid.")).toBeInTheDocument();
    });

    it('does not render any auto-place control anywhere in the DOM', async () => {
        const {adapter} = await setUpSeededSession();
        renderPrepScreen(adapter);
        await waitForBoardToLoad();

        expect(screen.queryByText(/auto.?place/i)).not.toBeInTheDocument();
    });

    it('tapping an empty cell opens the guided ship-placement popup', async () => {
        const user = userEvent.setup();
        const {adapter} = await setUpSeededSession();

        renderPrepScreen(adapter);
        await waitForBoardToLoad();

        await user.click(screen.getByRole('button', {name: /^A1,/}));

        expect(await screen.findByText('Choose a ship')).toBeInTheDocument();
    });

    it('guided popup flow: pick a ship then a direction places it with the popup\'s chosen direction', async () => {
        const user = userEvent.setup();
        const {adapter, sessionId, playerId} = await setUpSeededSession();
        const addShipSpy = vi.spyOn(adapter, 'addShip');

        renderPrepScreen(adapter);
        await waitForBoardToLoad();

        // Uses the Destroyer (size 3): a 1-cell ship confirms immediately with no direction
        // step at all (see ShipPlacementPopup.test.tsx), so it can't exercise this path.
        await user.click(screen.getByRole('button', {name: /^A1,/}));
        await screen.findByText('Choose a ship');
        const popup = screen.getByRole('dialog');

        // UKRAINIAN edition's 4 Patrol Boats are all size 1 — they collapse into a single
        // grouped row showing the count, not 4 separate rows.
        expect(within(popup).getAllByRole('button', {name: /Patrol Boat/})).toHaveLength(1);
        expect(within(popup).getByRole('button', {name: /Patrol Boat.*4 available/})).toBeInTheDocument();

        await user.click(within(popup).getByRole('button', {name: /Destroyer/}));
        await screen.findByText('Choose a direction');
        await user.click(within(popup).getByRole('button', {name: 'Vertical'}));

        await waitFor(() => {
            expect(addShipSpy).toHaveBeenCalledWith(sessionId, playerId, expect.any(String), {
                row: 0,
                column: 0,
            }, 'VERTICAL');
        });
        expect(await screen.findByText('1 of 10 ships placed')).toBeInTheDocument();
        // Popup closes itself once the placement completes.
        expect(screen.queryByText('Choose a direction')).not.toBeInTheDocument();
    });

    it('confirms a 1-cell ship immediately from the popup, with no direction step', async () => {
        const user = userEvent.setup();
        const {adapter, sessionId, playerId} = await setUpSeededSession();
        const addShipSpy = vi.spyOn(adapter, 'addShip');

        renderPrepScreen(adapter);
        await waitForBoardToLoad();

        await user.click(screen.getByRole('button', {name: /^A1,/}));
        const popup = await screen.findByRole('dialog');
        await user.click(within(popup).getByRole('button', {name: /Patrol Boat/}));

        await waitFor(() => {
            expect(addShipSpy).toHaveBeenCalledWith(sessionId, playerId, expect.any(String), {
                row: 0,
                column: 0,
            }, 'HORIZONTAL');
        });
        expect(await screen.findByText('1 of 10 ships placed')).toBeInTheDocument();
        expect(screen.queryByText('Choose a direction')).not.toBeInTheDocument();
    });

    it('the grouped popup count decreases after a placement', async () => {
        const user = userEvent.setup();
        const {adapter} = await setUpSeededSession();

        renderPrepScreen(adapter);
        await waitForBoardToLoad();

        // Place one Patrol Boat at A1 via the guided popup — a 1-cell ship confirms
        // immediately, with no direction step to click through.
        await placeShipViaPopup(user, /^A1,/, /Patrol Boat/);
        await waitFor(() => expect(screen.getByText('1 of 10 ships placed')).toBeInTheDocument());

        // Tap a different empty cell, far from A1's moat, and confirm the remaining count.
        await user.click(screen.getByRole('button', {name: /^F1,/}));
        await screen.findByText('Choose a ship');
        expect(
            within(screen.getByRole('dialog')).getByRole('button', {name: /Patrol Boat.*3 available/}),
        ).toBeInTheDocument();
    });

    it('shows a refresh button that re-fetches the session snapshot when clicked', async () => {
        const {adapter, sessionId, playerId} = await setUpSeededSession();

        renderPrepScreen(adapter);
        await waitForBoardToLoad();

        const getStageSpy = vi.spyOn(adapter, 'getStage');
        const getOpponentSpy = vi.spyOn(adapter, 'getOpponent');

        await act(async () => {
            fireEvent.click(screen.getByRole('button', {name: '⟳ Refresh'}));
        });

        expect(getStageSpy).toHaveBeenCalledWith(sessionId);
        expect(getOpponentSpy).toHaveBeenCalledWith(sessionId, playerId);
    });
});
