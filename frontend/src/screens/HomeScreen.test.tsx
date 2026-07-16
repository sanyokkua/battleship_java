import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import '../i18n';
import {HomeScreen} from './HomeScreen';

function renderHomeScreen() {
    return render(
        <MemoryRouter initialEntries={['/']}>
            <Routes>
                <Route path="/" element={<HomeScreen/>}/>
                <Route path="/new" element={<div>New route</div>}/>
                <Route path="/join" element={<div>Join route</div>}/>
            </Routes>
        </MemoryRouter>,
    );
}

describe('HomeScreen', () => {
    it('renders the tagline and note text', () => {
        renderHomeScreen();
        expect(screen.getByText('Sink the enemy fleet before they sink yours.')).toBeInTheDocument();
        expect(screen.getByText('Two players · 10×10 grid · classic rules')).toBeInTheDocument();
    });

    it('navigates to /new when "New Game" is clicked', async () => {
        const user = userEvent.setup();
        renderHomeScreen();

        await user.click(screen.getByRole('button', {name: 'New Game'}));

        expect(screen.getByText('New route')).toBeInTheDocument();
    });

    it('navigates to /join when "Join Game" is clicked', async () => {
        const user = userEvent.setup();
        renderHomeScreen();

        await user.click(screen.getByRole('button', {name: 'Join Game'}));

        expect(screen.getByText('Join route')).toBeInTheDocument();
    });
});
