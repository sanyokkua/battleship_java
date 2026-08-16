import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import i18n from '../../i18n';
import {QrCodeSheet} from './QrCodeSheet';

const toCanvasMock = vi.hoisted(() => vi.fn());
const qrModuleGate = vi.hoisted(() => {
    let resolve!: (module: Record<string, unknown>) => void;
    const promise = new Promise<Record<string, unknown>>((res) => {
        resolve = res;
    });
    return {promise, resolve};
});
vi.mock('qrcode', () => qrModuleGate.promise);

function deferred<T>() {
    let resolve!: (value: T | PromiseLike<T>) => void;
    let reject!: (reason?: unknown) => void;
    const promise = new Promise<T>((res, rej) => {
        resolve = res;
        reject = rej;
    });
    return {promise, resolve, reject};
}

describe('QrCodeSheet', () => {
    beforeEach(async () => {
        toCanvasMock.mockReset();
        await i18n.changeLanguage('en');
    });

    it('renders no QR presentation or renderer call while closed', () => {
        render(<QrCodeSheet open={false} url="https://example.test/join?id=session" onClose={vi.fn()}/>);

        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(toCanvasMock).not.toHaveBeenCalled();
    });

    it('keeps loading while the QR module is pending and ignores completion after Close', async () => {
        const user = userEvent.setup();
        const onClose = vi.fn();
        render(<QrCodeSheet open={true} url="https://example.test/join?id=session" onClose={onClose}/>);

        expect(screen.getByRole('status')).toHaveTextContent('Generating QR code…');
        expect(toCanvasMock).not.toHaveBeenCalled();
        await user.click(screen.getByRole('button', {name: 'Close'}));
        expect(onClose).toHaveBeenCalledTimes(1);

        qrModuleGate.resolve({toCanvas: toCanvasMock});
        await waitFor(() => expect(toCanvasMock).not.toHaveBeenCalled());
    });

    it('shows localized loading and hides the canvas until rendering fulfills', async () => {
        const renderGate = deferred<void>();
        toCanvasMock.mockReturnValue(renderGate.promise);

        render(<QrCodeSheet open={true} url="https://example.test/join?id=session" onClose={vi.fn()}/>);

        expect(screen.getByRole('dialog')).toBeInTheDocument();
        expect(screen.getByRole('status')).toHaveTextContent('Generating QR code…');
        expect(screen.getByRole('button', {name: 'Close'})).toBeInTheDocument();
        expect(screen.queryByRole('img')).not.toBeInTheDocument();

        const canvas = document.querySelector('canvas');
        expect(canvas).toHaveAttribute('hidden');
        await waitFor(() => expect(toCanvasMock).toHaveBeenCalledWith(
            canvas,
            'https://example.test/join?id=session',
            {width: 300, margin: 2, errorCorrectionLevel: 'M'},
        ));

        renderGate.resolve();

        await waitFor(() => {
            expect(screen.getByRole('img')).toHaveAttribute('aria-label', 'QR code for joining this game');
        });
        expect(screen.getByText('Scan this code with your phone to join the game.')).toBeInTheDocument();
        expect(canvas).not.toHaveAttribute('hidden');
    });

    it('shows a localized error and no ready canvas when rendering rejects', async () => {
        toCanvasMock.mockRejectedValue(new Error('renderer failed'));

        render(<QrCodeSheet open={true} url="https://example.test/join?id=session" onClose={vi.fn()}/>);

        expect(await screen.findByRole('alert')).toHaveTextContent('Unable to generate the QR code.');
        expect(screen.queryByRole('img')).not.toBeInTheDocument();
        expect(screen.getByRole('button', {name: 'Close'})).toBeInTheDocument();
    });

    it('ignores a late renderer result after close', async () => {
        const renderGate = deferred<void>();
        toCanvasMock.mockReturnValue(renderGate.promise);
        const onClose = vi.fn();
        const view = render(<QrCodeSheet open={true} url="https://example.test/join?id=session" onClose={onClose}/>);
        const canvas = document.querySelector('canvas');

        await waitFor(() => expect(toCanvasMock).toHaveBeenCalled());
        view.rerender(<QrCodeSheet open={false} url="https://example.test/join?id=session" onClose={onClose}/>);
        renderGate.resolve();

        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(screen.queryByRole('img')).not.toBeInTheDocument();
        expect(canvas).not.toBeInTheDocument();
    });

    it('starts a fresh generation after reopening with the current URL', async () => {
        const first = deferred<void>();
        const second = deferred<void>();
        toCanvasMock.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
        const onClose = vi.fn();
        const view = render(<QrCodeSheet open={true} url="https://example.test/join?id=one" onClose={onClose}/>);

        await waitFor(() => expect(toCanvasMock).toHaveBeenCalledTimes(1));
        first.resolve();
        await screen.findByRole('img');

        view.rerender(<QrCodeSheet open={false} url="https://example.test/join?id=one" onClose={onClose}/>);
        view.rerender(<QrCodeSheet open={true} url="https://example.test/join?id=two" onClose={onClose}/>);

        await waitFor(() => expect(toCanvasMock).toHaveBeenCalledTimes(2));
        expect(toCanvasMock.mock.calls[1][1]).toBe('https://example.test/join?id=two');
        second.resolve();
        await screen.findByRole('img');
    });

    it.each([
        ['en', 'Join with QR code', 'Scan this code with your phone to join the game.'],
        ['uk', 'Приєднатися за QR-кодом', 'Скануйте цей код телефоном, щоб приєднатися до гри.'],
    ])('renders the complete %s locale contract', async (locale, title, scan) => {
        await i18n.changeLanguage(locale);
        toCanvasMock.mockResolvedValue(undefined);

        render(<QrCodeSheet open={true} url="https://example.test/join?id=session" onClose={vi.fn()}/>);

        expect(await screen.findByRole('dialog')).toHaveAccessibleName(title);
        expect(await screen.findByRole('img')).toHaveAttribute('aria-label', locale === 'en'
            ? 'QR code for joining this game'
            : 'QR-код для приєднання до цієї гри');
        expect(screen.getByText(scan)).toBeInTheDocument();
        expect(screen.getByRole('button', {name: locale === 'en' ? 'Close' : 'Закрити'})).toBeInTheDocument();
        expect(screen.queryByRole('button', {name: /copy|download|export/i})).not.toBeInTheDocument();
    });

    it('closes through the localized Close action', async () => {
        const user = userEvent.setup();
        const onClose = vi.fn();
        toCanvasMock.mockResolvedValue(undefined);
        render(<QrCodeSheet open={true} url="https://example.test/join?id=session" onClose={onClose}/>);

        await user.click(await screen.findByRole('button', {name: 'Close'}));

        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('delegates Escape and backdrop dismissal to Sheet', async () => {
        const user = userEvent.setup();
        const onClose = vi.fn();
        toCanvasMock.mockResolvedValue(undefined);
        render(<QrCodeSheet open={true} url="https://example.test/join?id=session" onClose={onClose}/>);

        await user.keyboard('{Escape}');
        expect(onClose).toHaveBeenCalledTimes(1);

        await user.click(document.querySelector('.sheet-backdrop') as HTMLElement);
        expect(onClose).toHaveBeenCalledTimes(2);
    });

    it('renders the Ukrainian error state with a usable localized Close action', async () => {
        await i18n.changeLanguage('uk');
        toCanvasMock.mockRejectedValue(new Error('renderer failed'));

        render(<QrCodeSheet open={true} url="https://example.test/join?id=session" onClose={vi.fn()}/>);

        expect(await screen.findByRole('alert')).toHaveTextContent('Не вдалося створити QR-код.');
        expect(screen.getByRole('button', {name: 'Закрити'})).toBeInTheDocument();
        expect(screen.getByRole('dialog')).toHaveAttribute('aria-modal', 'true');
    });
});
