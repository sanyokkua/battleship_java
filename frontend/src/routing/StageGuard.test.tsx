import {render, screen} from '@testing-library/react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {StageGuard} from './StageGuard';
import {savePlayer, saveSession, saveStage} from '../services/GameBrowserStorage';
import type {ResponseCreatedPlayerDto} from '../logic/ApplicationTypes';

const PLAYER: ResponseCreatedPlayerDto = {playerId: 'p1', playerName: 'Ann'};

function renderGuard(initialPath: string, requiredStage?: string) {
    return render(
        <MemoryRouter initialEntries={[initialPath]}>
            <Routes>
                <Route path="/" element={<div>Home screen</div>}/>
                <Route path="/game/wait" element={<div>Wait screen</div>}/>
                <Route path="/game/preparation" element={<div>Preparation screen</div>}/>
                <Route path="/game/gameplay" element={<div>Gameplay screen</div>}/>
                <Route path="/game/results" element={<div>Results screen</div>}/>
                <Route
                    path="/guarded"
                    element={
                        <StageGuard requiredStage={requiredStage}>
                            <div>Guarded content</div>
                        </StageGuard>
                    }
                />
            </Routes>
        </MemoryRouter>,
    );
}

describe('StageGuard', () => {
    afterEach(() => {
        localStorage.clear();
    });

    it('redirects to "/" when there is no session at all', () => {
        renderGuard('/guarded', 'PREPARATION');
        expect(screen.getByText('Home screen')).toBeInTheDocument();
    });

    it('redirects to "/" when sessionId is present but player is missing', () => {
        saveSession('session-1');
        renderGuard('/guarded', 'PREPARATION');
        expect(screen.getByText('Home screen')).toBeInTheDocument();
    });

    it('redirects to "/" when player is present but sessionId is missing', () => {
        savePlayer(PLAYER);
        renderGuard('/guarded', 'PREPARATION');
        expect(screen.getByText('Home screen')).toBeInTheDocument();
    });

    it('redirects to the stage-correct route when the persisted stage does not match requiredStage', () => {
        saveSession('session-1');
        savePlayer(PLAYER);
        saveStage('IN_GAME');
        renderGuard('/guarded', 'PREPARATION');
        expect(screen.getByText('Gameplay screen')).toBeInTheDocument();
    });

    it('maps each persisted stage to its matching route on mismatch', () => {
        saveSession('session-1');
        savePlayer(PLAYER);

        saveStage('WAITING_FOR_PLAYERS');
        const {unmount: unmount1} = renderGuard('/guarded', 'FINISHED');
        expect(screen.getByText('Wait screen')).toBeInTheDocument();
        unmount1();

        saveStage('FINISHED');
        const {unmount: unmount2} = renderGuard('/guarded', 'PREPARATION');
        expect(screen.getByText('Results screen')).toBeInTheDocument();
        unmount2();
    });

    it('redirects to "/" when the persisted stage is null/unrecognized', () => {
        saveSession('session-1');
        savePlayer(PLAYER);
        renderGuard('/guarded', 'PREPARATION');
        expect(screen.getByText('Home screen')).toBeInTheDocument();
    });

    it('renders children when session + player are present and stage matches requiredStage', () => {
        saveSession('session-1');
        savePlayer(PLAYER);
        saveStage('PREPARATION');
        renderGuard('/guarded', 'PREPARATION');
        expect(screen.getByText('Guarded content')).toBeInTheDocument();
    });

    it('renders children when session + player are present and no requiredStage is given', () => {
        saveSession('session-1');
        savePlayer(PLAYER);
        renderGuard('/guarded');
        expect(screen.getByText('Guarded content')).toBeInTheDocument();
    });
});
