import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {ConfirmDialog} from './ConfirmDialog';

function renderDialog(overrides: Partial<React.ComponentProps<typeof ConfirmDialog>> = {}) {
    const onCancel = vi.fn();
    const onConfirm = vi.fn();
    const utils = render(
        <ConfirmDialog
            open={true}
            icon="🚪"
            title="Leave this game?"
            body="This game is in-memory and can't be resumed."
            cancelLabel="Cancel"
            confirmLabel="Leave"
            onCancel={onCancel}
            onConfirm={onConfirm}
            {...overrides}
        />,
    );
    return {...utils, onCancel, onConfirm};
}

describe('ConfirmDialog', () => {
    it('renders nothing when open=false', () => {
        const {container} = renderDialog({open: false});
        expect(container).toBeEmptyDOMElement();
    });

    it('renders the dialog with correct ARIA wiring when open=true', () => {
        renderDialog();
        const dialog = screen.getByRole('dialog');
        expect(dialog).toHaveAttribute('aria-modal', 'true');
        expect(dialog).toHaveAttribute('aria-labelledby');
        expect(dialog).toHaveAttribute('aria-describedby');
        expect(screen.getByText('Leave this game?')).toBeInTheDocument();
        expect(screen.getByText("This game is in-memory and can't be resumed.")).toBeInTheDocument();
    });

    it('calls onCancel when Escape is pressed', async () => {
        const user = userEvent.setup();
        const {onCancel} = renderDialog();

        await user.keyboard('{Escape}');

        expect(onCancel).toHaveBeenCalledTimes(1);
    });

    it('calls onCancel when the backdrop is clicked', async () => {
        const user = userEvent.setup();
        const {onCancel} = renderDialog();

        const backdrop = document.querySelector('.dialog-backdrop') as HTMLElement;
        await user.click(backdrop);

        expect(onCancel).toHaveBeenCalledTimes(1);
    });

    it('does not call onCancel when clicking inside the dialog', async () => {
        const user = userEvent.setup();
        const {onCancel} = renderDialog();

        await user.click(screen.getByText('Leave this game?'));

        expect(onCancel).not.toHaveBeenCalled();
    });

    it('calls onCancel when the Cancel button is clicked', async () => {
        const user = userEvent.setup();
        const {onCancel} = renderDialog();

        await user.click(screen.getByRole('button', {name: 'Cancel'}));

        expect(onCancel).toHaveBeenCalledTimes(1);
    });

    it('calls onConfirm when the Confirm button is clicked', async () => {
        const user = userEvent.setup();
        const {onConfirm} = renderDialog();

        await user.click(screen.getByRole('button', {name: 'Leave'}));

        expect(onConfirm).toHaveBeenCalledTimes(1);
    });

    it('moves focus into the dialog when opened', () => {
        renderDialog();
        const dialog = screen.getByRole('dialog');
        expect(dialog.contains(document.activeElement)).toBe(true);
    });

    it('traps Tab focus cycling within the dialog', async () => {
        const user = userEvent.setup();
        renderDialog();
        const dialog = screen.getByRole('dialog');

        // Tab through all focusable elements several times; focus should never
        // leave the dialog.
        for (let i = 0; i < 6; i++) {
            await user.tab();
            expect(dialog.contains(document.activeElement)).toBe(true);
        }
    });

    it('returns focus to the previously focused element on close', () => {
        const trigger = document.createElement('button');
        trigger.textContent = 'Open dialog';
        document.body.appendChild(trigger);
        trigger.focus();
        expect(document.activeElement).toBe(trigger);

        const {rerender, onCancel} = renderDialog();
        void onCancel;

        rerender(
            <ConfirmDialog
                open={false}
                title="Leave this game?"
                body="This game is in-memory and can't be resumed."
                cancelLabel="Cancel"
                confirmLabel="Leave"
                onCancel={() => {
                }}
                onConfirm={() => {
                }}
            />,
        );

        expect(document.activeElement).toBe(trigger);
        document.body.removeChild(trigger);
    });
});
