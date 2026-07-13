import {describe, expect, it, vi} from 'vitest';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import '../i18n';
import {NewGameScreen} from './NewGameScreen';
import {GameAdapterProvider} from '../adapters/GameAdapterContext';
import {ToastProvider} from '../widgets/feedback/ToastContext';
import {MockGameAdapter} from '../adapters/MockGameAdapter';

function renderNewGameScreen(adapter: MockGameAdapter) {
    return render(
        <GameAdapterProvider adapter={adapter}>
            <ToastProvider>
                <MemoryRouter initialEntries={['/new']}>
                    <Routes>
                        <Route path="/new" element={<NewGameScreen/>}/>
                        <Route path="/game/wait" element={<div>Wait route</div>}/>
                    </Routes>
                </MemoryRouter>
            </ToastProvider>
        </GameAdapterProvider>,
    );
}

async function waitForCardsToLoad() {
    await waitFor(() => {
        expect(screen.getAllByRole('radio')).toHaveLength(2);
    });
}

describe('NewGameScreen', () => {
    it('renders one mode card per edition with correct names, descriptions and ship-size chip counts', async () => {
        const adapter = new MockGameAdapter();
        const {container} = renderNewGameScreen(adapter);
        await waitForCardsToLoad();

        expect(screen.getByRole('radio', {name: /Ukrainian/})).toBeInTheDocument();
        expect(screen.getByRole('radio', {name: /Milton Bradley/})).toBeInTheDocument();
        expect(screen.getByText('10 ships · sizes 1–4 · 20 cells')).toBeInTheDocument();
        expect(screen.getByText('10 ships · sizes 2–5 · 30 cells')).toBeInTheDocument();

        const cards = container.querySelectorAll('.mode-card');
        expect(cards).toHaveLength(2);
        cards.forEach((card) => {
            expect(card.querySelectorAll('.mc-ships i')).toHaveLength(10);
        });
    });

    it('defaults to the first edition selected, and selecting another card updates the selection', async () => {
        const adapter = new MockGameAdapter();
        renderNewGameScreen(adapter);
        await waitForCardsToLoad();

        const ukr = screen.getByRole('radio', {name: /Ukrainian/});
        const mb = screen.getByRole('radio', {name: /Milton Bradley/});
        expect(ukr).toHaveAttribute('aria-checked', 'true');
        expect(mb).toHaveAttribute('aria-checked', 'false');

        const user = userEvent.setup();
        await user.click(mb);

        expect(mb).toHaveAttribute('aria-checked', 'true');
        expect(ukr).toHaveAttribute('aria-checked', 'false');
    });

    it('shows an inline validation error for a too-short name on blur, and clears it once valid', async () => {
        const adapter = new MockGameAdapter();
        renderNewGameScreen(adapter);
        await waitForCardsToLoad();

        const user = userEvent.setup();
        const nameInput = screen.getByLabelText('Player name');

        await user.type(nameInput, 'A');
        await user.tab();

        expect(await screen.findByText('Name must be at least 2 characters.')).toBeInTheDocument();

        await user.type(nameInput, 'lice');

        await waitFor(() => {
            expect(screen.queryByText('Name must be at least 2 characters.')).not.toBeInTheDocument();
        });
    });

    it('submits by calling createSession, createPlayer, getStage in order and navigates to /game/wait', async () => {
        const adapter = new MockGameAdapter();
        const createSessionSpy = vi.spyOn(adapter, 'createSession');
        const createPlayerSpy = vi.spyOn(adapter, 'createPlayer');
        const getStageSpy = vi.spyOn(adapter, 'getStage');

        renderNewGameScreen(adapter);
        await waitForCardsToLoad();

        const user = userEvent.setup();
        await user.type(screen.getByLabelText('Player name'), 'Superman');
        await user.click(screen.getByRole('button', {name: 'Start new game'}));

        await waitFor(() => {
            expect(screen.getByText('Wait route')).toBeInTheDocument();
        });

        expect(createSessionSpy).toHaveBeenCalledWith('UKRAINIAN');
        expect(createPlayerSpy).toHaveBeenCalledTimes(1);
        expect(getStageSpy).toHaveBeenCalledTimes(1);

        const sessionCallOrder = createSessionSpy.mock.invocationCallOrder[0];
        const playerCallOrder = createPlayerSpy.mock.invocationCallOrder[0];
        const stageCallOrder = getStageSpy.mock.invocationCallOrder[0];
        expect(sessionCallOrder).toBeLessThan(playerCallOrder);
        expect(playerCallOrder).toBeLessThan(stageCallOrder);
    });

    it('does not submit (no adapter calls, no navigation) when the name is invalid', async () => {
        const adapter = new MockGameAdapter();
        const createSessionSpy = vi.spyOn(adapter, 'createSession');

        renderNewGameScreen(adapter);
        await waitForCardsToLoad();

        const submitButton = screen.getByRole('button', {name: 'Start new game'});
        expect(submitButton).toBeDisabled();

        const user = userEvent.setup();
        await user.click(submitButton);

        expect(createSessionSpy).not.toHaveBeenCalled();
        expect(screen.queryByText('Wait route')).not.toBeInTheDocument();
    });
});
