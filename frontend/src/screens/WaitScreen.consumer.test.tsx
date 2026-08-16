import {waitFor, fireEvent, render, screen} from '@testing-library/react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import '../i18n';
import {ToastProvider} from '../widgets/feedback/ToastContext';
import type {ResponseCreatedPlayerDto} from '../logic/ApplicationTypes';
import {WaitScreen} from './WaitScreen';

const sessionGuardMock = vi.hoisted(() => vi.fn());
const waitRoomMock = vi.hoisted(() => vi.fn());
const copyMock = vi.hoisted(() => vi.fn());
const toCanvasMock = vi.hoisted(() => vi.fn().mockResolvedValue(undefined));

vi.mock('../config/appConfig', () => ({APP_BASE_PATH: '/battleship'}));
vi.mock('../hooks/useSessionGuard', () => ({useSessionGuard: sessionGuardMock}));
vi.mock('../hooks/useWaitRoom', () => ({useWaitRoom: waitRoomMock}));
vi.mock('copy-to-clipboard', () => ({default: (text: string) => copyMock(text)}));
vi.mock('qrcode', () => ({toCanvas: toCanvasMock}));

const player: ResponseCreatedPlayerDto = {playerId: 'player-1', playerName: 'Alice'};
const sessionId = 'space & plus+slash/percent% unicode Ž';

function renderWaitScreen() {
    return render(
        <ToastProvider>
            <MemoryRouter initialEntries={['/game/wait']}>
                <Routes>
                    <Route path="/game/wait" element={<WaitScreen/>}/>
                </Routes>
            </MemoryRouter>
        </ToastProvider>,
    );
}

describe('WaitScreen sharing consumer conformance', () => {
    beforeEach(() => {
        sessionGuardMock.mockReset().mockReturnValue({
            sessionId,
            player,
            stage: 'WAITING_FOR_PLAYERS',
        });
        waitRoomMock.mockReset().mockReturnValue({
            opponent: {playerName: '', ready: false},
            stage: 'WAITING_FOR_PLAYERS',
            loading: false,
            error: null,
            refresh: vi.fn(),
        });
        copyMock.mockReset().mockResolvedValue(true);
        toCanvasMock.mockReset().mockResolvedValue(undefined);
    });

    it('passes the same configured-subpath URL to Copy link and qrcode.toCanvas', async () => {
        renderWaitScreen();

        fireEvent.click(screen.getByRole('button', {name: 'Copy link'}));
        fireEvent.click(screen.getByRole('button', {name: 'Show QR code'}));

        await waitFor(() => expect(toCanvasMock).toHaveBeenCalled());

        const copiedUrl = copyMock.mock.calls[0][0];
        const qrUrl = toCanvasMock.mock.calls[0][1];
        expect(copiedUrl).toBe(
            'http://localhost:3000/battleship/join?id=space+%26+plus%2Bslash%2Fpercent%25+unicode+%C5%BD',
        );
        expect(qrUrl).toBe(copiedUrl);
        expect(new URL(qrUrl).pathname.match(/\/battleship/g)).toHaveLength(1);
    });
});
