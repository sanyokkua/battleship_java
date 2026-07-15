import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {type ShipPlacementOption, ShipPlacementPopup} from './ShipPlacementPopup';

const BASE_LABELS = {
    pickShipTitle: 'Pick a ship',
    pickDirectionTitle: 'Pick a direction',
    emptyStateMessage: 'No ship fits here',
    closeLabel: 'Close',
    backLabel: 'Back',
    horizontalLabel: 'Horizontal',
    verticalLabel: 'Vertical',
    cellSingularLabel: 'cell',
    cellPluralLabel: 'cells',
    availableLabel: 'available',
};

const OPTIONS: ShipPlacementOption[] = [
    {shipId: 'patrol-1', shipSize: 1, typeName: 'Patrol Boat', count: 4, directions: ['HORIZONTAL', 'VERTICAL']},
    {shipId: 'destroyer-1', shipSize: 3, typeName: 'Destroyer', count: 1, directions: ['VERTICAL']},
];

function renderPopup(overrides: Partial<React.ComponentProps<typeof ShipPlacementPopup>> = {}) {
    const onClose = vi.fn();
    const onConfirm = vi.fn();
    const utils = render(
        <ShipPlacementPopup
            open={true}
            options={OPTIONS}
            onClose={onClose}
            onConfirm={onConfirm}
            {...BASE_LABELS}
            {...overrides}
        />,
    );
    return {...utils, onClose, onConfirm};
}

describe('ShipPlacementPopup', () => {
    it('renders nothing when open=false', () => {
        const {container} = renderPopup({open: false});
        expect(container).toBeEmptyDOMElement();
    });

    it('lists eligible ships with their size when open', () => {
        renderPopup();
        expect(screen.getByRole('button', {name: /Patrol Boat.*1 cell\b/})).toBeInTheDocument();
        expect(screen.getByRole('button', {name: /Destroyer.*3 cells/})).toBeInTheDocument();
    });

    it('shows each group\'s count so a single row can represent several interchangeable ships', () => {
        renderPopup();
        expect(screen.getByRole('button', {name: /Patrol Boat.*4 available/})).toBeInTheDocument();
        expect(screen.getByRole('button', {name: /Destroyer.*1 available/})).toBeInTheDocument();
        // Exactly one row per group — not one row per underlying ship (4 Patrol Boats).
        expect(screen.getAllByRole('button', {name: /Patrol Boat/})).toHaveLength(1);
    });

    it('shows an empty-state message and a close action when no ships are eligible', async () => {
        const user = userEvent.setup();
        const {onClose} = renderPopup({options: []});

        expect(screen.getByText('No ship fits here')).toBeInTheDocument();
        await user.click(screen.getByRole('button', {name: 'Close'}));

        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('advances to the direction step showing only that ship\'s valid directions', async () => {
        const user = userEvent.setup();
        renderPopup();

        await user.click(screen.getByRole('button', {name: /Destroyer/}));

        expect(screen.getByRole('button', {name: 'Vertical'})).toBeInTheDocument();
        expect(screen.queryByRole('button', {name: 'Horizontal'})).not.toBeInTheDocument();
    });

    it('calls onConfirm with the chosen ship and direction', async () => {
        const user = userEvent.setup();
        const {onConfirm} = renderPopup();

        await user.click(screen.getByRole('button', {name: /Destroyer/}));
        await user.click(screen.getByRole('button', {name: 'Vertical'}));

        expect(onConfirm).toHaveBeenCalledWith('destroyer-1', 'VERTICAL');
    });

    it('confirms immediately for a 1-cell ship, without ever showing a direction step', async () => {
        const user = userEvent.setup();
        const {onConfirm} = renderPopup();

        await user.click(screen.getByRole('button', {name: /Patrol Boat/}));

        expect(onConfirm).toHaveBeenCalledWith('patrol-1', 'HORIZONTAL');
        expect(screen.queryByText('Pick a direction')).not.toBeInTheDocument();
        expect(screen.queryByRole('button', {name: 'Horizontal'})).not.toBeInTheDocument();
        expect(screen.queryByRole('button', {name: 'Vertical'})).not.toBeInTheDocument();
    });

    it('the Back button on the direction step returns to the ship list', async () => {
        const user = userEvent.setup();
        renderPopup();

        await user.click(screen.getByRole('button', {name: /Destroyer/}));
        await user.click(screen.getByRole('button', {name: 'Back'}));

        expect(screen.getByRole('button', {name: /Patrol Boat/})).toBeInTheDocument();
        expect(screen.getByRole('button', {name: /Destroyer/})).toBeInTheDocument();
    });

    it('disables ship-step buttons while an action is in flight', () => {
        renderPopup({disabled: true});
        expect(screen.getByRole('button', {name: /Patrol Boat/})).toBeDisabled();
        expect(screen.getByRole('button', {name: /Destroyer/})).toBeDisabled();
    });

    it('disables direction-step buttons once an action starts (parent flips disabled after confirm)', async () => {
        const user = userEvent.setup();
        const {rerender} = renderPopup();

        // Reach the direction step while still enabled (a disabled ship button couldn't be clicked).
        // Uses Destroyer (size > 1) — a 1-cell ship confirms immediately and never reaches this step.
        await user.click(screen.getByRole('button', {name: /Destroyer/}));
        expect(screen.getByRole('button', {name: 'Vertical'})).toBeEnabled();

        // Parent sets disabled=true once the confirm action is in flight; the same mounted
        // instance (still on the direction step) should now render its buttons disabled.
        rerender(
            <ShipPlacementPopup
                open={true}
                options={OPTIONS}
                disabled={true}
                onClose={vi.fn()}
                onConfirm={vi.fn()}
                {...BASE_LABELS}
            />,
        );

        expect(screen.getByRole('button', {name: 'Vertical'})).toBeDisabled();
        expect(screen.getByRole('button', {name: 'Back'})).toBeDisabled();
    });

    it('resets back to the ship-picking step after being closed and reopened', async () => {
        const user = userEvent.setup();
        const {rerender} = renderPopup();

        // Advance to the direction step, then close (without confirming) and reopen.
        await user.click(screen.getByRole('button', {name: /Destroyer/}));
        expect(screen.getByRole('button', {name: 'Vertical'})).toBeInTheDocument();

        rerender(
            <ShipPlacementPopup open={false} options={OPTIONS} onClose={vi.fn()}
                                onConfirm={vi.fn()} {...BASE_LABELS} />,
        );
        rerender(
            <ShipPlacementPopup open={true} options={OPTIONS} onClose={vi.fn()} onConfirm={vi.fn()} {...BASE_LABELS} />,
        );

        expect(screen.getByRole('button', {name: /Patrol Boat/})).toBeInTheDocument();
        expect(screen.queryByRole('button', {name: 'Vertical'})).not.toBeInTheDocument();
    });
});
