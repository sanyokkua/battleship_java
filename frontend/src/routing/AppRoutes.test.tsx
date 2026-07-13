import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import '../i18n';
import {AppRoutes} from './AppRoutes';
import {GameAdapterProvider} from '../adapters/GameAdapterContext';
import {MockGameAdapter} from '../adapters/MockGameAdapter';
import {ToastProvider} from '../widgets/feedback/ToastContext';
import {ToastStack} from '../widgets/feedback/ToastStack';
import {savePlayer, saveSession, saveStage} from '../services/GameBrowserStorage';

// Deterministic non-overlapping layout, same pattern used by the screen tickets'
// own fixtures (GameplayScreen.test.tsx / ResultsScreen.test.tsx).
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

async function setUpInGameSession(adapter: MockGameAdapter) {
    const sessionId = await adapter.createSession('UKRAINIAN');
    const p1 = await adapter.createPlayer(sessionId, 'Alice');
    const p2 = await adapter.createPlayer(sessionId, 'Bob');
    await placeAllShips(adapter, sessionId, p1.playerId);
    await placeAllShips(adapter, sessionId, p2.playerId);
    await adapter.setReady(sessionId, p1.playerId);
    await adapter.setReady(sessionId, p2.playerId);
    return {sessionId, p1, p2};
}

async function setUpFinishedSession(adapter: MockGameAdapter) {
    const {sessionId, p1, p2} = await setUpInGameSession(adapter);
    // p1 is active first after both are ready; hand the turn to p2 with a
    // guaranteed-miss shot (a MISS switches the active player; a HIT keeps it).
    await adapter.shoot(sessionId, p1.playerId, {row: 9, column: 9});
    const loserState = await adapter.getGameState(sessionId, p1.playerId);
    const loserShipCells: { row: number; col: number }[] = [];
    for (const row of loserState.playerField) {
        for (const cell of row) {
            if (cell.ship) {
                loserShipCells.push({row: cell.row, col: cell.col});
            }
        }
    }
    for (const cell of loserShipCells) {
        await adapter.shoot(sessionId, p2.playerId, {row: cell.row, column: cell.col});
    }
    return {sessionId, p1, p2};
}

function renderAt(path: string, adapter: MockGameAdapter = new MockGameAdapter()) {
    return render(
        <GameAdapterProvider adapter={adapter}>
            <ToastProvider>
                <MemoryRouter initialEntries={[path]}>
                    <AppRoutes/>
                </MemoryRouter>
                <ToastStack/>
            </ToastProvider>
        </GameAdapterProvider>,
    );
}

describe('AppRoutes', () => {
    afterEach(() => {
        localStorage.clear();
    });

    it('renders the Home screen at "/"', () => {
        renderAt('/');
        expect(screen.getByText('Sink the enemy fleet before they sink yours.')).toBeInTheDocument();
    });

    it('renders the New Game screen at "/new"', async () => {
        renderAt('/new');
        expect(await screen.findByRole('heading', {name: 'New Game'})).toBeInTheDocument();
    });

    it('renders the Join Game screen at "/join"', async () => {
        renderAt('/join');
        expect(await screen.findByRole('heading', {name: 'Join Game'})).toBeInTheDocument();
    });

    it('redirects "/game/wait" to "/" without a session (StageGuard)', () => {
        renderAt('/game/wait');
        expect(screen.getByText('Sink the enemy fleet before they sink yours.')).toBeInTheDocument();
    });

    it('renders the Wait screen when session/player/stage line up', async () => {
        const adapter = new MockGameAdapter();
        const sessionId = await adapter.createSession('UKRAINIAN');
        const p1 = await adapter.createPlayer(sessionId, 'Ann');
        saveSession(sessionId);
        savePlayer(p1);
        saveStage('WAITING_FOR_PLAYERS');
        renderAt('/game/wait', adapter);
        expect(await screen.findByText('Hello, Ann!')).toBeInTheDocument();
    });

    it('renders the Preparation screen when session/player/stage line up', async () => {
        const adapter = new MockGameAdapter();
        const sessionId = await adapter.createSession('UKRAINIAN');
        const p1 = await adapter.createPlayer(sessionId, 'Ann');
        await adapter.createPlayer(sessionId, 'Bob');
        saveSession(sessionId);
        savePlayer(p1);
        saveStage('PREPARATION');
        renderAt('/game/preparation', adapter);
        expect(await screen.findByText('Place your fleet')).toBeInTheDocument();
    });

    it('renders the Gameplay screen when session/player/stage line up', async () => {
        const adapter = new MockGameAdapter();
        const {sessionId, p1} = await setUpInGameSession(adapter);
        saveSession(sessionId);
        savePlayer(p1);
        saveStage('IN_GAME');
        renderAt('/game/gameplay', adapter);
        expect(await screen.findByText('Alice')).toBeInTheDocument();
    });

    it('renders the Results screen when session/player/stage line up', async () => {
        const adapter = new MockGameAdapter();
        const {sessionId, p2} = await setUpFinishedSession(adapter);
        saveSession(sessionId);
        savePlayer(p2);
        saveStage('FINISHED');
        renderAt('/game/results', adapter);
        expect(await screen.findByText('Victory!')).toBeInTheDocument();
    });

    it('redirects an unknown path to "/"', () => {
        renderAt('/this/does/not/exist');
        expect(screen.getByText('Sink the enemy fleet before they sink yours.')).toBeInTheDocument();
    });
});
