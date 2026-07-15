import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {Sheet} from './Sheet';

describe('Sheet', () => {
    it('renders nothing when open=false', () => {
        const {container} = render(
            <Sheet open={false} title="Pick a ship" onClose={vi.fn()}>
                <p>content</p>
            </Sheet>,
        );
        expect(container).toBeEmptyDOMElement();
    });

    it('renders the dialog with correct ARIA wiring and children when open=true', () => {
        render(
            <Sheet open={true} title="Pick a ship" onClose={vi.fn()}>
                <p>Ship list here</p>
            </Sheet>,
        );
        const dialog = screen.getByRole('dialog');
        expect(dialog).toHaveAttribute('aria-modal', 'true');
        const labelledBy = dialog.getAttribute('aria-labelledby');
        expect(labelledBy).toBeTruthy();
        expect(document.getElementById(labelledBy!)).toHaveTextContent('Pick a ship');
        expect(screen.getByText('Ship list here')).toBeInTheDocument();
    });

    it('calls onClose when Escape is pressed', async () => {
        const user = userEvent.setup();
        const onClose = vi.fn();
        render(
            <Sheet open={true} title="Pick a ship" onClose={onClose}>
                <button>Option</button>
            </Sheet>,
        );

        await user.keyboard('{Escape}');

        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('calls onClose when the backdrop is clicked', async () => {
        const user = userEvent.setup();
        const onClose = vi.fn();
        render(
            <Sheet open={true} title="Pick a ship" onClose={onClose}>
                <button>Option</button>
            </Sheet>,
        );

        const backdrop = document.querySelector('.sheet-backdrop') as HTMLElement;
        await user.click(backdrop);

        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('does not call onClose when clicking inside the sheet content', async () => {
        const user = userEvent.setup();
        const onClose = vi.fn();
        render(
            <Sheet open={true} title="Pick a ship" onClose={onClose}>
                <button>Option</button>
            </Sheet>,
        );

        await user.click(screen.getByText('Option'));

        expect(onClose).not.toHaveBeenCalled();
    });

    it('moves focus into the sheet when opened', () => {
        render(
            <Sheet open={true} title="Pick a ship" onClose={vi.fn()}>
                <button>Option</button>
            </Sheet>,
        );
        const dialog = screen.getByRole('dialog');
        expect(dialog.contains(document.activeElement)).toBe(true);
    });

    it('gives two simultaneously open Sheets distinct aria-labelledby ids', () => {
        render(
            <>
                <Sheet open={true} title="First sheet" onClose={vi.fn()}>
                    <p>a</p>
                </Sheet>
                <Sheet open={true} title="Second sheet" onClose={vi.fn()}>
                    <p>b</p>
                </Sheet>
            </>,
        );
        const dialogs = screen.getAllByRole('dialog');
        const [firstId, secondId] = dialogs.map((d) => d.getAttribute('aria-labelledby'));
        expect(firstId).not.toBe(secondId);
        expect(document.getElementById(firstId!)).toHaveTextContent('First sheet');
        expect(document.getElementById(secondId!)).toHaveTextContent('Second sheet');
    });
});
