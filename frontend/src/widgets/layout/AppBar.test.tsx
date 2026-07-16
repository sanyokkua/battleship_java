import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import '../../i18n';
import i18n from '../../i18n';
import {AppBar} from './AppBar';
import {savePlayer, saveSession, saveStage} from '../../services/GameBrowserStorage';
import type {ResponseCreatedPlayerDto} from '../../logic/ApplicationTypes';

const PLAYER: ResponseCreatedPlayerDto = {playerId: 'p1', playerName: 'Ann'};

function renderAppBar(initialPath = '/') {
    return render(
        <MemoryRouter initialEntries={[initialPath]}>
            <AppBar/>
            <Routes>
                <Route path="/" element={<div>Home route</div>}/>
                <Route path="/new" element={<div>New route</div>}/>
                <Route path="/join" element={<div>Join route</div>}/>
                <Route path="/game/preparation" element={<div>Preparation route</div>}/>
                <Route path="/game/gameplay" element={<div>Gameplay route</div>}/>
            </Routes>
        </MemoryRouter>,
    );
}

describe('AppBar', () => {
    afterEach(async () => {
        localStorage.clear();
        await i18n.changeLanguage('en');
    });

    it('always renders Home/New/Join links', () => {
        renderAppBar();
        expect(screen.getByRole('link', {name: 'Home'})).toBeInTheDocument();
        expect(screen.getByRole('link', {name: 'New'})).toBeInTheDocument();
        expect(screen.getByRole('link', {name: 'Join'})).toBeInTheDocument();
    });

    it('does not render Preparation/Gameplay links when there is no session+player', () => {
        renderAppBar();
        expect(screen.queryByRole('link', {name: 'Preparation'})).not.toBeInTheDocument();
        expect(screen.queryByRole('link', {name: 'Gameplay'})).not.toBeInTheDocument();
    });

    it('renders Preparation/Gameplay links when session+player are present', () => {
        saveSession('s1');
        savePlayer(PLAYER);
        renderAppBar();
        expect(screen.getByRole('link', {name: 'Preparation'})).toBeInTheDocument();
        expect(screen.getByRole('link', {name: 'Gameplay'})).toBeInTheDocument();
    });

    it('marks the active route link with the "on" class', () => {
        renderAppBar('/new');
        const homeLink = screen.getByRole('link', {name: 'Home'});
        const newLink = screen.getByRole('link', {name: 'New'});
        expect(newLink.className).toContain('on');
        expect(homeLink.className).not.toContain('on');
    });

    it('toggles the hamburger menu open/closed with correct aria-expanded', async () => {
        const user = userEvent.setup();
        renderAppBar();
        const burger = screen.getByRole('button', {name: 'Menu'});
        expect(burger).toHaveAttribute('aria-expanded', 'false');

        await user.click(burger);
        expect(burger).toHaveAttribute('aria-expanded', 'true');

        await user.click(burger);
        expect(burger).toHaveAttribute('aria-expanded', 'false');
    });

    it('switches language via i18n.changeLanguage and updates the active button', async () => {
        const user = userEvent.setup();
        renderAppBar();

        const ukButton = screen.getByRole('button', {name: 'УКР'});
        const enButton = screen.getByRole('button', {name: 'EN'});
        expect(enButton.className).toContain('on');

        await user.click(ukButton);

        expect(i18n.language).toBe('uk');
        expect(ukButton.className).toContain('on');
    });

    it('navigates immediately on nav click when there is no active game', async () => {
        const user = userEvent.setup();
        renderAppBar('/');

        await user.click(screen.getByRole('link', {name: 'New'}));

        expect(screen.getByText('New route')).toBeInTheDocument();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('opens the leave-confirmation dialog instead of navigating when session is mid-game (PREPARATION)', async () => {
        const user = userEvent.setup();
        saveSession('s1');
        savePlayer(PLAYER);
        saveStage('PREPARATION');
        renderAppBar('/game/preparation');

        await user.click(screen.getByRole('link', {name: 'Home'}));

        expect(screen.getByRole('dialog')).toBeInTheDocument();
        expect(screen.getByText('Preparation route')).toBeInTheDocument();
    });

    it('Cancel closes the dialog and keeps the current route', async () => {
        const user = userEvent.setup();
        saveSession('s1');
        savePlayer(PLAYER);
        saveStage('PREPARATION');
        renderAppBar('/game/preparation');

        await user.click(screen.getByRole('link', {name: 'Home'}));
        await user.click(screen.getByRole('button', {name: 'Cancel'}));

        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(screen.getByText('Preparation route')).toBeInTheDocument();
    });

    it('Confirm clears storage and navigates to the intended destination', async () => {
        const user = userEvent.setup();
        saveSession('s1');
        savePlayer(PLAYER);
        saveStage('PREPARATION');
        renderAppBar('/game/preparation');

        await user.click(screen.getByRole('link', {name: 'Home'}));
        await user.click(screen.getByRole('button', {name: 'Leave game'}));

        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(screen.getByText('Home route')).toBeInTheDocument();
        expect(localStorage.getItem('session_str')).toBeNull();
        expect(localStorage.getItem('player_obj')).toBeNull();
        expect(localStorage.getItem('gameStage_str')).toBeNull();
    });
});
