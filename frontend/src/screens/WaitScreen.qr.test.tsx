import {fireEvent, render, screen} from '@testing-library/react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import '../i18n';
import {ToastProvider} from '../widgets/feedback/ToastContext';
import type {ResponseCreatedPlayerDto} from '../logic/ApplicationTypes';
import {WaitScreen} from './WaitScreen';

const sessionGuardMock = vi.hoisted(() => vi.fn());
const waitRoomMock = vi.hoisted(() => vi.fn());
const qrPropsMock = vi.hoisted(() => vi.fn());
const copyMock = vi.hoisted(() => vi.fn());

vi.mock('../hooks/useSessionGuard', () => ({useSessionGuard: sessionGuardMock}));
vi.mock('../hooks/useWaitRoom', () => ({useWaitRoom: waitRoomMock}));
vi.mock('copy-to-clipboard', () => ({default: (text: string) => copyMock(text)}));
vi.mock('../widgets/sharing/QrCodeSheet', () => ({
    QrCodeSheet: (props: {open: boolean; url: string; onClose: () => void}) => {
        qrPropsMock(props);
        return props.open
            ? <div data-testid="qr-sheet" role="dialog">
                <button type="button" onClick={props.onClose}>Close</button>
            </div>
            : null;
    },
}));

const player: ResponseCreatedPlayerDto = {
    playerId: 'player-1',
    playerName: 'Alice',
};

function renderWaitScreen() {
    return render(
        <ToastProvider>
            <MemoryRouter initialEntries={['/game/wait']}>
                <Routes>
                    <Route path="/game/wait" element={<WaitScreen/>}/>
                    <Route path="/game/preparation" element={<div>Preparation route</div>}/>
                </Routes>
            </MemoryRouter>
        </ToastProvider>,
    );
}

function setWaitingContext(overrides: {
    sessionId?: string | null;
    player?: ResponseCreatedPlayerDto | null;
    stage?: string | null;
    loading?: boolean;
} = {}) {
    sessionGuardMock.mockReturnValue({
        sessionId: 'session-1',
        player,
        stage: 'WAITING_FOR_PLAYERS',
        ...overrides,
    });
    waitRoomMock.mockReturnValue({
        opponent: {playerName: '', ready: false},
        stage: 'WAITING_FOR_PLAYERS',
        loading: false,
        error: null,
        refresh: vi.fn(),
        ...overrides,
    });
}

describe('WaitScreen QR integration', () => {
    beforeEach(() => {
        sessionGuardMock.mockReset();
        waitRoomMock.mockReset();
        qrPropsMock.mockReset();
        copyMock.mockReset().mockResolvedValue(true);
        setWaitingContext({player});
    });

    it('keeps the QR presentation closed until selection and shares one canonical URL', () => {
        renderWaitScreen();

        expect(screen.getByRole('button', {name: 'Show QR code'})).toBeInTheDocument();
        expect(screen.queryByTestId('qr-sheet')).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', {name: 'Copy link'}));
        expect(copyMock).toHaveBeenCalledWith('http://localhost:3000/join?id=session-1');

        fireEvent.click(screen.getByRole('button', {name: 'Show QR code'}));

        expect(screen.getByTestId('qr-sheet')).toBeInTheDocument();
        expect(qrPropsMock).toHaveBeenLastCalledWith(expect.objectContaining({
            open: true,
            url: 'http://localhost:3000/join?id=session-1',
        }));
    });

    it.each([
        ['missing session', {sessionId: null, player}],
        ['missing player', {sessionId: 'session-1', player: null}],
        ['blank session', {sessionId: '  ', player}],
        ['non-waiting stage', {sessionId: 'session-1', player, stage: 'INITIALIZED'}],
        ['unresolved loading', {sessionId: 'session-1', player, loading: true}],
    ])('withholds Show QR code for %s context', (_name, overrides) => {
        setWaitingContext(overrides);

        renderWaitScreen();

        expect(screen.queryByRole('button', {name: 'Show QR code'})).not.toBeInTheDocument();
    });

    it('opens the QR presentation before any renderer completion is required', () => {
        renderWaitScreen();

        fireEvent.click(screen.getByRole('button', {name: 'Show QR code'}));

        expect(screen.getByRole('dialog')).toBeInTheDocument();
        expect(qrPropsMock).toHaveBeenLastCalledWith(expect.objectContaining({open: true}));
    });

    it('passes one byte-for-byte encoded URL to Copy link and the QR consumer', () => {
        const sessionId = 'space & plus+slash/percent% unicode Ž';
        setWaitingContext({sessionId, player});
        renderWaitScreen();

        fireEvent.click(screen.getByRole('button', {name: 'Copy link'}));
        fireEvent.click(screen.getByRole('button', {name: 'Show QR code'}));

        const copiedUrl = copyMock.mock.calls[0][0];
        const qrUrl = qrPropsMock.mock.calls[qrPropsMock.mock.calls.length - 1]?.[0].url;
        expect(copiedUrl).toBe(
            'http://localhost:3000/join?id=space+%26+plus%2Bslash%2Fpercent%25+unicode+%C5%BD',
        );
        expect(qrUrl).toBe(copiedUrl);
    });
});
