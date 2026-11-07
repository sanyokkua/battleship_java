import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { Dialog } from '../src/components/Dialog';
import { ProblemAlert } from '../src/components/ProblemAlert';

const showModal = vi.fn(function (this: HTMLDialogElement) {
    this.open = true;
});

beforeEach(() => {
    HTMLDialogElement.prototype.showModal = showModal;
    HTMLDialogElement.prototype.close = function () {
        this.open = false;
    };
});
afterEach(() => {
    showModal.mockClear();
});

function closeNatively(dialog: HTMLElement) {
    (dialog as HTMLDialogElement).open = false;
    dialog.dispatchEvent(new Event('close'));
}

// Chrome's second Escape closes a modal without a cancelable event.
it('reopens a non-dismissible dialog that the browser closed natively', () => {
    render(
        <Dialog open titleId="t" dismissible={false} onClose={vi.fn()}>
            <h2 id="t">Checking</h2>
        </Dialog>,
    );
    expect(showModal).toHaveBeenCalledOnce();

    closeNatively(document.querySelector('dialog') as HTMLElement);

    expect(showModal).toHaveBeenCalledTimes(2);
});

it('does not reopen a dialog that was closed by the application', () => {
    const { rerender } = render(
        <Dialog open titleId="t" dismissible={false} onClose={vi.fn()}>
            <h2 id="t">Checking</h2>
        </Dialog>,
    );
    rerender(
        <Dialog open={false} titleId="t" dismissible={false} onClose={vi.fn()}>
            <h2 id="t">Checking</h2>
        </Dialog>,
    );

    closeNatively(document.querySelector('dialog') as HTMLElement);

    expect(showModal).toHaveBeenCalledOnce();
});

it('keeps the changing wait hint out of the alert so it is not re-announced', () => {
    render(<ProblemAlert messageKey="problem.rateLimited" waitSeconds={7} />);

    expect(screen.getByRole('alert')).not.toHaveTextContent('7');
    expect(screen.getByText(/7/)).toBeInTheDocument();
});
