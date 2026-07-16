import {useRef} from 'react';
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {useFocusTrap} from './useFocusTrap';

function TestDialog({open, onClose, empty}: { open: boolean; onClose: () => void; empty?: boolean }) {
    const ref = useRef<HTMLDivElement>(null);
    useFocusTrap(ref, open, onClose);

    if (!open) {
        return null;
    }

    return (
        <div ref={ref} tabIndex={-1} data-testid="dialog">
            {!empty && (
                <>
                    <button>First</button>
                    <button>Second</button>
                    <button>Last</button>
                </>
            )}
        </div>
    );
}

describe('useFocusTrap', () => {
    it('moves focus to the first focusable element inside the container when opened', () => {
        render(<TestDialog open={true} onClose={vi.fn()}/>);
        expect(document.activeElement).toBe(screen.getByText('First'));
    });

    it('focuses the container itself when there are no focusable elements inside', () => {
        render(<TestDialog open={true} onClose={vi.fn()} empty/>);
        expect(document.activeElement).toBe(screen.getByTestId('dialog'));
    });

    it('calls onClose when Escape is pressed', async () => {
        const user = userEvent.setup();
        const onClose = vi.fn();
        render(<TestDialog open={true} onClose={onClose}/>);

        await user.keyboard('{Escape}');

        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('wraps Tab from the last element back to the first', async () => {
        const user = userEvent.setup();
        render(<TestDialog open={true} onClose={vi.fn()}/>);

        screen.getByText('Last').focus();
        await user.tab();

        expect(document.activeElement).toBe(screen.getByText('First'));
    });

    it('wraps Shift+Tab from the first element back to the last', async () => {
        const user = userEvent.setup();
        render(<TestDialog open={true} onClose={vi.fn()}/>);

        screen.getByText('First').focus();
        await user.tab({shift: true});

        expect(document.activeElement).toBe(screen.getByText('Last'));
    });

    it('restores focus to the previously focused element once closed', () => {
        const trigger = document.createElement('button');
        trigger.textContent = 'Open';
        document.body.appendChild(trigger);
        trigger.focus();

        const {rerender} = render(<TestDialog open={true} onClose={vi.fn()}/>);
        expect(document.activeElement).not.toBe(trigger);

        rerender(<TestDialog open={false} onClose={vi.fn()}/>);

        expect(document.activeElement).toBe(trigger);
        document.body.removeChild(trigger);
    });
});
