import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {act, render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import '../i18n';
import {MockGameAdapter} from '../adapters/MockGameAdapter';
import {GameAdapterProvider} from '../adapters/GameAdapterContext';
import {ToastProvider} from '../widgets/feedback/ToastContext';
import {ToastStack} from '../widgets/feedback/ToastStack';
import {savePlayer, saveSession, saveStage} from '../services/GameBrowserStorage';
import type {CellDto} from '../logic/ApplicationTypes';
import {formatCoordinateLabel} from '../logic/boardCoordinates';
import {GameplayScreen} from './GameplayScreen';

/**
 * Fake-timers gotcha (see WaitScreen.test.tsx / useWaitRoom.test.tsx): MockGameAdapter's
 * methods are async, so with fake timers, plain vi.advanceTimersByTime does not flush the
 * microtasks backing those awaits. Use shouldAdvanceTime + advanceTimersByTimeAsync wrapped
 * in act() to avoid flakiness.
 */

// Deterministic non-overlapping placement, mirrored from adapters/MockGameAdapter.test.ts's
// placeAllShips helper (kept local since that file's helper isn't exported).
async function placeAllShips(adapter: MockGameAdapter, sessionId: string, playerId: string): Promise<void> {
    const prep = await adapter.getPreparationState(sessionId, playerId);
    let row = 0;
    let column = 0;
    for (const ship of prep.ships) {
        await adapter.addShip(sessionId, playerId, ship.shipId, {row, column}, 'HORIZONTAL');
        if (column === 0) {
            column = 5;
        } else {
            column = 0;
            row += 2;
        }
    }
}

function findEmptyCell(field: CellDto[][]): { row: number; col: number } {
    for (const row of field) {
        for (const cell of row) {
            if (!cell.ship) {
                return {row: cell.row, col: cell.col};
            }
        }
    }
    throw new Error('No empty cell found');
}

function findShipCellOfSize(field: CellDto[][], size: number): { row: number; col: number } {
    for (const row of field) {
        for (const cell of row) {
            if (cell.ship && cell.ship.shipSize === size) {
                return {row: cell.row, col: cell.col};
            }
        }
    }
    throw new Error(`No ship cell of size ${size} found`);
}

/**
 * Full create -> join -> prepare-both-sides -> ready flow, reaching IN_GAME with
 * `activePlayerId` set to p1 (session.players[0]). Reusable fixture pattern for the
 * Results screen ticket, which needs the same IN_GAME/FINISHED setup:
 *   1. adapter.createSession(edition)
 *   2. adapter.createPlayer(sessionId, name) x2 -> stage flips WAITING_FOR_PLAYERS -> PREPARATION
 *   3. placeAllShips(adapter, sessionId, playerId) for both players (deterministic non-overlapping layout)
 *   4. adapter.setReady(sessionId, playerId) for both players -> stage flips to IN_GAME, activePlayerId = p1
 */
async function setUpInGameSession(adapter: MockGameAdapter, edition: string = 'UKRAINIAN') {
    const sessionId = await adapter.createSession(edition);
    const p1 = await adapter.createPlayer(sessionId, 'Alice');
    const p2 = await adapter.createPlayer(sessionId, 'Bob');

    await placeAllShips(adapter, sessionId, p1.playerId);
    await placeAllShips(adapter, sessionId, p2.playerId);

    await adapter.setReady(sessionId, p1.playerId);
    await adapter.setReady(sessionId, p2.playerId);

    return {sessionId, p1: p1.playerId, p2: p2.playerId, p1Name: p1.playerName, p2Name: p2.playerName};
}

function renderGameplayScreen() {
    return render(
        <GameAdapterProvider adapter={adapterInstance}>
            <ToastProvider>
                <MemoryRouter initialEntries={['/game/gameplay']}>
                    <Routes>
                        <Route path="/game/gameplay" element={<GameplayScreen/>}/>
                        <Route path="/game/results" element={<div>Results route</div>}/>
                    </Routes>
                </MemoryRouter>
                <ToastStack/>
            </ToastProvider>
        </GameAdapterProvider>,
    );
}

let adapterInstance: MockGameAdapter;

describe('GameplayScreen', () => {
    beforeEach(() => {
        localStorage.clear();
        adapterInstance = new MockGameAdapter();
        vi.useFakeTimers({shouldAdvanceTime: true});
    });

    afterEach(() => {
        vi.useRealTimers();
        localStorage.clear();
    });

    it('renders PlayerCard health/ship stats from the DTO fields', async () => {
        const {sessionId, p1, p1Name, p2Name} = await setUpInGameSession(adapterInstance);
        saveSession(sessionId);
        savePlayer({playerId: p1, playerName: p1Name});
        saveStage('IN_GAME');

        renderGameplayScreen();

        await waitFor(() => expect(screen.getByText(p1Name)).toBeInTheDocument());
        expect(screen.getByText(p2Name)).toBeInTheDocument();

        // Ukrainian edition: 20 total cells, 10 ships, no shots fired yet -> both players
        // show their full alive-cell count and 10/10 ships.
        const youCard = screen.getByText(p1Name).closest<HTMLElement>('.player-card')!;
        const foeCard = screen.getByText(p2Name).closest<HTMLElement>('.player-card')!;
        expect(within(youCard).getByText('20')).toBeInTheDocument();
        expect(within(youCard).getByText('10 / 10')).toBeInTheDocument();
        expect(within(foeCard).getByText('20')).toBeInTheDocument();
        expect(within(foeCard).getByText('10 / 10')).toBeInTheDocument();
    });

    it('reflects isPlayerActive in the TurnBanner for both states', async () => {
        const {sessionId, p1, p2, p1Name, p2Name} = await setUpInGameSession(adapterInstance);

        // p1 is active first (session.players[0]).
        saveSession(sessionId);
        savePlayer({playerId: p1, playerName: p1Name});
        saveStage('IN_GAME');
        const {unmount} = renderGameplayScreen();
        await waitFor(() => expect(screen.getByText('Your turn — fire!')).toBeInTheDocument());
        unmount();

        // p2 is not active.
        localStorage.clear();
        saveSession(sessionId);
        savePlayer({playerId: p2, playerName: p2Name});
        saveStage('IN_GAME');
        renderGameplayScreen();
        await waitFor(() => expect(screen.getByText(`${p1Name} is taking a shot…`)).toBeInTheDocument());
    });

    it('tapping a target cell on the player\'s turn calls shoot', async () => {
        const {sessionId, p1, p1Name} = await setUpInGameSession(adapterInstance);
        saveSession(sessionId);
        savePlayer({playerId: p1, playerName: p1Name});
        saveStage('IN_GAME');

        const shootSpy = vi.spyOn(adapterInstance, 'shoot');
        const user = userEvent.setup({advanceTimers: vi.advanceTimersByTime});
        renderGameplayScreen();

        await waitFor(() => expect(screen.getByText('Your turn — fire!')).toBeInTheDocument());

        const targetPanel = document.querySelector('.bp-target')!;
        const targetCell = within(targetPanel as HTMLElement).getAllByRole('button')[0];
        await user.click(targetCell);

        await waitFor(() => expect(shootSpy).toHaveBeenCalledTimes(1));
        expect(shootSpy).toHaveBeenCalledWith(sessionId, p1, expect.objectContaining({row: 0, column: 0}));
    });

    it('tapping a target cell when NOT the player\'s turn shows the "not your turn" toast and does not call shoot', async () => {
        const {sessionId, p2, p2Name} = await setUpInGameSession(adapterInstance);
        // p2 is not active right after setup (p1 goes first).
        saveSession(sessionId);
        savePlayer({playerId: p2, playerName: p2Name});
        saveStage('IN_GAME');

        const shootSpy = vi.spyOn(adapterInstance, 'shoot');
        const user = userEvent.setup({advanceTimers: vi.advanceTimersByTime});
        renderGameplayScreen();

        await waitFor(() => expect(screen.getByText(/is taking a shot/)).toBeInTheDocument());

        const targetPanel = document.querySelector('.bp-target')!;
        const targetCell = within(targetPanel as HTMLElement).getAllByRole('button')[0];
        await user.click(targetCell);

        await waitFor(() => expect(screen.getByText('Not your turn')).toBeInTheDocument());
        expect(screen.getByText('Wait for your opponent to take their shot.')).toBeInTheDocument();
        expect(shootSpy).not.toHaveBeenCalled();
    });

    it('clicking an already-shot target cell again does not call shoot a second time', async () => {
        const {sessionId, p1, p1Name} = await setUpInGameSession(adapterInstance);
        saveSession(sessionId);
        savePlayer({playerId: p1, playerName: p1Name});
        saveStage('IN_GAME');

        const shootSpy = vi.spyOn(adapterInstance, 'shoot');
        const user = userEvent.setup({advanceTimers: vi.advanceTimersByTime});
        renderGameplayScreen();

        await waitFor(() => expect(screen.getByText('Your turn — fire!')).toBeInTheDocument());

        const targetPanel = document.querySelector('.bp-target')!;
        const targetCell = within(targetPanel as HTMLElement).getAllByRole('button')[0];
        await user.click(targetCell);

        await waitFor(() => expect(shootSpy).toHaveBeenCalledTimes(1));

        // Once shot, that cell must re-render as a non-interactive div (BoardCell fix) — confirm
        // the click target is no longer a button before attempting the second click.
        const cellAtOrigin = (targetPanel as HTMLElement).querySelectorAll('.cell')[0];
        await waitFor(() => expect(cellAtOrigin.tagName).toBe('DIV'));

        await user.click(cellAtOrigin);

        // Defense-in-depth: even if something re-triggered handleShot for that same location,
        // it must not call shoot() a second time.
        expect(shootSpy).toHaveBeenCalledTimes(1);
    });

    it('own (fleet) board is read-only: clicking a fleet-board cell does not call shoot', async () => {
        const {sessionId, p1, p1Name} = await setUpInGameSession(adapterInstance);
        saveSession(sessionId);
        savePlayer({playerId: p1, playerName: p1Name});
        saveStage('IN_GAME');

        const shootSpy = vi.spyOn(adapterInstance, 'shoot');
        const user = userEvent.setup({advanceTimers: vi.advanceTimersByTime});
        renderGameplayScreen();

        await waitFor(() => expect(screen.getByText('Your turn — fire!')).toBeInTheDocument());

        // Switch to the fleet tab (mobile tab-toggle) to make sure the fleet board's cells are
        // reachable/interactable in this render, then attempt to click one.
        await user.click(screen.getByRole('tab', {name: 'Your fleet'}));

        const fleetPanel = document.querySelector('.bp-fleet')!;
        // Own-board cells are rendered as <div>, not <button>, when there's no onCellClick handler
        // (Board/BoardCell's readonly + no-handler convention) — assert no clickable buttons exist.
        expect(within(fleetPanel as HTMLElement).queryAllByRole('button')).toHaveLength(0);

        const firstCell = (fleetPanel as HTMLElement).querySelector('.cell')!;
        await user.click(firstCell);

        expect(shootSpy).not.toHaveBeenCalled();
    });

    it('board tabs toggle which board panel is visible', async () => {
        const {sessionId, p1, p1Name} = await setUpInGameSession(adapterInstance);
        saveSession(sessionId);
        savePlayer({playerId: p1, playerName: p1Name});
        saveStage('IN_GAME');

        const user = userEvent.setup({advanceTimers: vi.advanceTimersByTime});
        renderGameplayScreen();

        await waitFor(() => expect(screen.getByText('Your turn — fire!')).toBeInTheDocument());

        const targetPanel = document.querySelector('.bp-target')!;
        const fleetPanel = document.querySelector('.bp-fleet')!;

        expect(targetPanel).not.toHaveClass('hide');
        expect(fleetPanel).toHaveClass('hide');

        await user.click(screen.getByRole('tab', {name: 'Your fleet'}));

        expect(targetPanel).toHaveClass('hide');
        expect(fleetPanel).not.toHaveClass('hide');

        await user.click(screen.getByRole('tab', {name: 'Target grid'}));

        expect(targetPanel).not.toHaveClass('hide');
        expect(fleetPanel).toHaveClass('hide');
    });

    it('auto-switches to the Fleet tab when the turn passes to the opponent', async () => {
        const {sessionId, p1, p2, p1Name} = await setUpInGameSession(adapterInstance);
        saveSession(sessionId);
        savePlayer({playerId: p1, playerName: p1Name});
        saveStage('IN_GAME');

        const user = userEvent.setup({advanceTimers: vi.advanceTimersByTime});
        renderGameplayScreen();

        await waitFor(() => expect(screen.getByText('Your turn — fire!')).toBeInTheDocument());

        const targetPanel = document.querySelector('.bp-target')!;
        const fleetPanel = document.querySelector('.bp-fleet')!;
        expect(targetPanel).not.toHaveClass('hide');
        expect(fleetPanel).toHaveClass('hide');

        // Fire a real UI shot at a guaranteed-empty cell (found via the opponent's own
        // preparation-state field, which reveals ship placement) so it resolves as a MISS and
        // flips the active player to the opponent — without clicking any tab manually.
        const p2Prep = await adapterInstance.getPreparationState(sessionId, p2);
        const {row, col} = findEmptyCell(p2Prep.field);
        const targetCell = within(targetPanel as HTMLElement).getAllByRole('button')[row * 10 + col];
        await user.click(targetCell);

        await waitFor(() => expect(screen.getByText(/is taking a shot/)).toBeInTheDocument());
        await waitFor(() => expect(fleetPanel).not.toHaveClass('hide'));
        expect(targetPanel).toHaveClass('hide');
    });

    it('auto-switches back to the Target tab when the turn returns', async () => {
        const {sessionId, p1, p2, p1Name} = await setUpInGameSession(adapterInstance);
        saveSession(sessionId);
        savePlayer({playerId: p1, playerName: p1Name});
        saveStage('IN_GAME');

        const user = userEvent.setup({advanceTimers: vi.advanceTimersByTime});
        renderGameplayScreen();

        await waitFor(() => expect(screen.getByText('Your turn — fire!')).toBeInTheDocument());

        const targetPanel = document.querySelector('.bp-target')!;
        const fleetPanel = document.querySelector('.bp-fleet')!;

        // Fire as p1 at a guaranteed-empty cell (MISS) to flip the turn to p2.
        const p2Prep = await adapterInstance.getPreparationState(sessionId, p2);
        const {row, col} = findEmptyCell(p2Prep.field);
        const targetCell = within(targetPanel as HTMLElement).getAllByRole('button')[row * 10 + col];
        await user.click(targetCell);

        await waitFor(() => expect(fleetPanel).not.toHaveClass('hide'));

        // Take the opponent's turn via a direct adapter call (a guaranteed-empty cell on p1's
        // own field, from p1's preparation state) so it resolves as a MISS and flips the active
        // player back to p1.
        const p1Prep = await adapterInstance.getPreparationState(sessionId, p1);
        const opponentShot = findEmptyCell(p1Prep.field);
        // Pushed to the mounted screen as soon as it happens — no poll wait needed.
        await act(async () => {
            await adapterInstance.shoot(sessionId, p2, {row: opponentShot.row, column: opponentShot.col});
        });

        await waitFor(() => expect(screen.getByText('Your turn — fire!')).toBeInTheDocument());
        await waitFor(() => expect(targetPanel).not.toHaveClass('hide'));
        expect(fleetPanel).toHaveClass('hide');
    });

    it('does not force the tab away from a manual mid-turn switch on an unrelated state push', async () => {
        const {sessionId, p1, p2, p1Name} = await setUpInGameSession(adapterInstance);
        saveSession(sessionId);
        savePlayer({playerId: p1, playerName: p1Name});
        saveStage('IN_GAME');

        const user = userEvent.setup({advanceTimers: vi.advanceTimersByTime});
        renderGameplayScreen();

        await waitFor(() => expect(screen.getByText('Your turn — fire!')).toBeInTheDocument());

        // Manually switch to the Fleet tab mid-turn (player remains active throughout).
        await user.click(screen.getByRole('tab', {name: 'Your fleet'}));

        const targetPanel = document.querySelector('.bp-target')!;
        const fleetPanel = document.querySelector('.bp-fleet')!;
        expect(fleetPanel).not.toHaveClass('hide');
        expect(targetPanel).toHaveClass('hide');

        // A push where p1 remains active — a HIT keeps the shooter's turn, per classic
        // Battleship rules (see MockGameAdapter.shoot) — must not reset the tab back to Target.
        const p2Prep = await adapterInstance.getPreparationState(sessionId, p2);
        const hitAt = findShipCellOfSize(p2Prep.field, 1);
        await act(async () => {
            await adapterInstance.shoot(sessionId, p1, {row: hitAt.row, column: hitAt.col});
        });

        expect(fleetPanel).not.toHaveClass('hide');
        expect(targetPanel).toHaveClass('hide');
    });

    it('redirects to /game/results once hasWinner becomes true', async () => {
        const {sessionId, p1, p2, p1Name} = await setUpInGameSession(adapterInstance);
        saveSession(sessionId);
        savePlayer({playerId: p1, playerName: p1Name});
        saveStage('IN_GAME');

        renderGameplayScreen();
        await waitFor(() => expect(screen.getByText('Your turn — fire!')).toBeInTheDocument());

        // Drive p2's fleet down to zero alive ships directly via the adapter (bypassing the UI)
        // so p1's next shot wins the game: shoot every one of p2's ship cells.
        const p2Prep = await adapterInstance.getPreparationState(sessionId, p2);
        const shipCells: { row: number; col: number }[] = [];
        for (const row of p2Prep.field) {
            for (const cell of row) {
                if (cell.ship) shipCells.push({row: cell.row, col: cell.col});
            }
        }
        expect(shipCells.length).toBeGreaterThan(0);

        await act(async () => {
            for (const cell of shipCells) {
                await adapterInstance.shoot(sessionId, p1, {row: cell.row, column: cell.col});
            }
        });

        await expect(adapterInstance.getStage(sessionId)).resolves.toBe('FINISHED');

        // Each shot above was pushed to the mounted screen as it happened — hasWinner=true is
        // already reflected, no poll wait needed.
        await waitFor(() => expect(screen.getByText('Results route')).toBeInTheDocument());
        expect(localStorage.getItem('gameStage_str')).toBe('FINISHED');
    });

    it('sanity: findEmptyCell/findShipCellOfSize helpers work against a prepared field', async () => {
        const {sessionId, p1} = await setUpInGameSession(adapterInstance);
        const state = await adapterInstance.getPreparationState(sessionId, p1);
        expect(findEmptyCell(state.field)).toBeDefined();
        expect(findShipCellOfSize(state.field, 1)).toBeDefined();
    });

    describe('opponent-shot toast', () => {
        // Flips the active turn from p1 to p2 via a real UI click on a guaranteed-empty
        // cell (a MISS), so the subsequent backdoor shot as p2 is legal — MockGameAdapter
        // enforces turn order even for direct adapter calls (see shoot()'s
        // activePlayerId check), matching the pattern already used at line ~332 above.
        async function flipTurnToP2(sessionId: string, p2: string, user: ReturnType<typeof userEvent.setup>) {
            const p2Prep = await adapterInstance.getPreparationState(sessionId, p2);
            const {row, col} = findEmptyCell(p2Prep.field);
            const targetPanel = document.querySelector('.bp-target')!;
            const targetCell = within(targetPanel as HTMLElement).getAllByRole('button')[row * 10 + col];
            await user.click(targetCell);
            await waitFor(() => expect(screen.getByText(/is taking a shot/)).toBeInTheDocument());
        }

        it('shows a toast with the coordinate and "miss" when the opponent fires at an empty cell', async () => {
            const {sessionId, p1, p2, p1Name} = await setUpInGameSession(adapterInstance);
            saveSession(sessionId);
            savePlayer({playerId: p1, playerName: p1Name});
            saveStage('IN_GAME');

            const user = userEvent.setup({advanceTimers: vi.advanceTimersByTime});
            renderGameplayScreen();
            await waitFor(() => expect(screen.getByText('Your turn — fire!')).toBeInTheDocument());

            await flipTurnToP2(sessionId, p2, user);

            const p1Prep = await adapterInstance.getPreparationState(sessionId, p1);
            const at = findEmptyCell(p1Prep.field);
            // Pushed to the mounted screen as soon as it happens — no poll wait needed.
            await act(async () => {
                await adapterInstance.shoot(sessionId, p2, {row: at.row, column: at.col});
            });

            const label = formatCoordinateLabel(at.row, at.col);
            expect(await screen.findByText('Opponent missed')).toBeInTheDocument();
            expect(screen.getByText(`The opponent fired at ${label} and missed.`)).toBeInTheDocument();
        });

        it('shows a toast with the coordinate and "hit" when the opponent hits a ship without sinking it', async () => {
            const {sessionId, p1, p2, p1Name} = await setUpInGameSession(adapterInstance);
            saveSession(sessionId);
            savePlayer({playerId: p1, playerName: p1Name});
            saveStage('IN_GAME');

            const user = userEvent.setup({advanceTimers: vi.advanceTimersByTime});
            renderGameplayScreen();
            await waitFor(() => expect(screen.getByText('Your turn — fire!')).toBeInTheDocument());

            await flipTurnToP2(sessionId, p2, user);

            // findShipCellOfSize returns the FIRST cell of a size-2+ ship (its bow) — hitting
            // only that one cell hits without sinking, since HORIZONTAL placement means its
            // other cell(s) remain unshot.
            const p1Prep = await adapterInstance.getPreparationState(sessionId, p1);
            const at = findShipCellOfSize(p1Prep.field, 2);
            // Pushed to the mounted screen as soon as it happens — no poll wait needed.
            await act(async () => {
                await adapterInstance.shoot(sessionId, p2, {row: at.row, column: at.col});
            });

            const label = formatCoordinateLabel(at.row, at.col);
            expect(await screen.findByText('Your ship was hit!')).toBeInTheDocument();
            expect(screen.getByText(`The opponent hit your ship at ${label}.`)).toBeInTheDocument();
            expect(screen.queryByText('Your ship was sunk!')).not.toBeInTheDocument();
        });

        it('shows a "sunk" toast (not "hit") when the opponent\'s shot sinks a ship', async () => {
            const {sessionId, p1, p2, p1Name} = await setUpInGameSession(adapterInstance);
            saveSession(sessionId);
            savePlayer({playerId: p1, playerName: p1Name});
            saveStage('IN_GAME');

            const user = userEvent.setup({advanceTimers: vi.advanceTimersByTime});
            renderGameplayScreen();
            await waitFor(() => expect(screen.getByText('Your turn — fire!')).toBeInTheDocument());

            await flipTurnToP2(sessionId, p2, user);

            // A size-1 Patrol Boat sinks in exactly one shot.
            const p1Prep = await adapterInstance.getPreparationState(sessionId, p1);
            const at = findShipCellOfSize(p1Prep.field, 1);
            // Pushed to the mounted screen as soon as it happens — no poll wait needed.
            await act(async () => {
                await adapterInstance.shoot(sessionId, p2, {row: at.row, column: at.col});
            });

            const label = formatCoordinateLabel(at.row, at.col);
            expect(await screen.findByText('Your ship was sunk!')).toBeInTheDocument();
            expect(screen.getByText(`The opponent sank your ship at ${label}.`)).toBeInTheDocument();
            expect(screen.queryByText('Your ship was hit!')).not.toBeInTheDocument();
        });

        it('does not spam "missed" toasts for the moat cells auto-revealed when a ship is sunk', async () => {
            const {sessionId, p1, p2, p1Name} = await setUpInGameSession(adapterInstance);
            saveSession(sessionId);
            savePlayer({playerId: p1, playerName: p1Name});
            saveStage('IN_GAME');

            const user = userEvent.setup({advanceTimers: vi.advanceTimersByTime});
            renderGameplayScreen();
            await waitFor(() => expect(screen.getByText('Your turn — fire!')).toBeInTheDocument());

            await flipTurnToP2(sessionId, p2, user);

            // A size-1 Patrol Boat sinks in exactly one shot, auto-revealing up to 8 moat cells
            // around it (MockGameAdapter now mirrors the real backend's kill-reveal side effect —
            // see MockGameAdapter.test.ts). Only the kill itself should ever produce a toast.
            const p1Prep = await adapterInstance.getPreparationState(sessionId, p1);
            const at = findShipCellOfSize(p1Prep.field, 1);
            await act(async () => {
                await adapterInstance.shoot(sessionId, p2, {row: at.row, column: at.col});
            });

            expect(await screen.findByText('Your ship was sunk!')).toBeInTheDocument();
            expect(screen.queryByText('Opponent missed')).not.toBeInTheDocument();
            expect(screen.queryByText('Your ship was hit!')).not.toBeInTheDocument();
        });

        it('does not replay a pre-existing hit as a new toast on mount', async () => {
            const {sessionId, p1, p2, p1Name} = await setUpInGameSession(adapterInstance);

            // Seed a hit on p1's board entirely via the backdoor, before the screen ever mounts:
            // flip the turn to p2 (a miss on p2's own board), then p2 hits p1.
            const p2Prep = await adapterInstance.getPreparationState(sessionId, p2);
            const missAt = findEmptyCell(p2Prep.field);
            await adapterInstance.shoot(sessionId, p1, {row: missAt.row, column: missAt.col});

            const p1Prep = await adapterInstance.getPreparationState(sessionId, p1);
            const hitAt = findShipCellOfSize(p1Prep.field, 2);
            await adapterInstance.shoot(sessionId, p2, {row: hitAt.row, column: hitAt.col});

            saveSession(sessionId);
            savePlayer({playerId: p1, playerName: p1Name});
            saveStage('IN_GAME');

            renderGameplayScreen();
            await waitFor(() => expect(screen.getByText(p1Name)).toBeInTheDocument());

            expect(screen.queryByText('Your ship was hit!')).not.toBeInTheDocument();
            expect(screen.queryByText('Your ship was sunk!')).not.toBeInTheDocument();
            expect(screen.queryByText('Opponent missed')).not.toBeInTheDocument();
        });

        it('shows one toast per newly-revealed cell when multiple opponent shots land in the same batch', async () => {
            const {sessionId, p1, p2, p1Name} = await setUpInGameSession(adapterInstance);
            saveSession(sessionId);
            savePlayer({playerId: p1, playerName: p1Name});
            saveStage('IN_GAME');

            const user = userEvent.setup({advanceTimers: vi.advanceTimersByTime});
            renderGameplayScreen();
            await waitFor(() => expect(screen.getByText('Your turn — fire!')).toBeInTheDocument());

            // Flip the turn via a real UI click (also seeds the diff's "previous field" baseline
            // via the push this triggers), then land BOTH opponent shots purely through direct
            // adapter calls — bypassing the mounted hook's own shoot() entirely, matching how a
            // real opponent's moves arrive via push rather than this player's own action. Wrapping
            // all three in one act() lets React coalesce the resulting pushes into a single diff
            // pass, which must still reveal both newly-hit cells at once.
            await flipTurnToP2(sessionId, p2, user);

            const p1Prep = await adapterInstance.getPreparationState(sessionId, p1);
            const allEmpty = p1Prep.field.flat().filter((c) => !c.ship);
            const [first, second] = allEmpty;
            // findEmptyCell alone would risk re-finding the SAME cell flipTurnToP2 already shot
            // (it only checks for `!ship`, not `hasShot`) — filter both out explicitly.
            const p2Prep = await adapterInstance.getPreparationState(sessionId, p2);
            const p2MissAt = p2Prep.field.flat().find((c) => !c.ship && !c.hasShot)!;

            await act(async () => {
                await adapterInstance.shoot(sessionId, p2, {row: first.row, column: first.col}); // miss -> flips to p1
                await adapterInstance.shoot(sessionId, p1, {row: p2MissAt.row, column: p2MissAt.col}); // miss -> flips back to p2
                await adapterInstance.shoot(sessionId, p2, {row: second.row, column: second.col}); // miss -> flips to p1
            });

            const firstLabel = formatCoordinateLabel(first.row, first.col);
            const secondLabel = formatCoordinateLabel(second.row, second.col);
            expect(await screen.findByText(`The opponent fired at ${firstLabel} and missed.`)).toBeInTheDocument();
            expect(screen.getByText(`The opponent fired at ${secondLabel} and missed.`)).toBeInTheDocument();
        });
    });
});
