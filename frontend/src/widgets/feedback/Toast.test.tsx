import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '../../i18n';
import {Toast} from './Toast';
import type {ToastData} from './ToastContext';

function makeToast(overrides: Partial<ToastData> = {}): ToastData {
    return {
        id: 't1',
        variant: 'ok',
        title: 'Ship placed',
        message: 'Ship positioned on the board.',
        ...overrides,
    };
}

describe('Toast', () => {
    it('renders the title and message', () => {
        render(<Toast toast={makeToast()} onDismiss={vi.fn()}/>);
        expect(screen.getByText('Ship placed')).toBeInTheDocument();
        expect(screen.getByText('Ship positioned on the board.')).toBeInTheDocument();
    });

    it.each([
        ['ok', '✅'],
        ['info', '⏳'],
        ['warn', '🎯'],
        ['err', '⚠️'],
    ] as const)('renders the %s variant icon and border class', (variant, icon) => {
        const {container} = render(<Toast toast={makeToast({variant})} onDismiss={vi.fn()}/>);
        expect(screen.getByText(icon)).toBeInTheDocument();
        expect(container.querySelector(`.toast.${variant}`)).not.toBeNull();
    });

    it('calls onDismiss with the toast id when the dismiss button is clicked', async () => {
        const user = userEvent.setup();
        const onDismiss = vi.fn();
        render(<Toast toast={makeToast({id: 'abc'})} onDismiss={onDismiss}/>);

        await user.click(screen.getByRole('button', {name: /dismiss/i}));

        expect(onDismiss).toHaveBeenCalledWith('abc');
    });
});
