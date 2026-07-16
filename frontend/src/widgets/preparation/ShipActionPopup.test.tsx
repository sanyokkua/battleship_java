import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {ShipActionPopup} from './ShipActionPopup';

const BASE_LABELS = {
    rotateLabel: 'Rotate',
    removeLabel: 'Remove',
    cellSingularLabel: 'cell',
    cellPluralLabel: 'cells',
};

function renderPopup(overrides: Partial<React.ComponentProps<typeof ShipActionPopup>> = {}) {
    const onClose = vi.fn();
    const onRotate = vi.fn();
    const onRemove = vi.fn();
    const utils = render(
        <ShipActionPopup
            open={true}
            shipTypeName="Destroyer"
            shipSize={3}
            canRotate={true}
            onClose={onClose}
            onRotate={onRotate}
            onRemove={onRemove}
            {...BASE_LABELS}
            {...overrides}
        />,
    );
    return {...utils, onClose, onRotate, onRemove};
}

describe('ShipActionPopup', () => {
    it('renders nothing when open=false', () => {
        const {container} = renderPopup({open: false});
        expect(container).toBeEmptyDOMElement();
    });

    it('shows the ship name and size', () => {
        renderPopup();
        expect(screen.getByText('Destroyer')).toBeInTheDocument();
        expect(screen.getByText('3 cells')).toBeInTheDocument();
    });

    it('shows the Rotate button when canRotate is true', () => {
        renderPopup({canRotate: true});
        expect(screen.getByRole('button', {name: 'Rotate'})).toBeInTheDocument();
    });

    it('hides the Rotate button when canRotate is false', () => {
        renderPopup({canRotate: false});
        expect(screen.queryByRole('button', {name: 'Rotate'})).not.toBeInTheDocument();
    });

    it('always shows the Remove button', () => {
        renderPopup({canRotate: false});
        expect(screen.getByRole('button', {name: 'Remove'})).toBeInTheDocument();
    });

    it('calls onRotate when Rotate is clicked', async () => {
        const user = userEvent.setup();
        const {onRotate} = renderPopup();

        await user.click(screen.getByRole('button', {name: 'Rotate'}));

        expect(onRotate).toHaveBeenCalledTimes(1);
    });

    it('calls onRemove when Remove is clicked', async () => {
        const user = userEvent.setup();
        const {onRemove} = renderPopup();

        await user.click(screen.getByRole('button', {name: 'Remove'}));

        expect(onRemove).toHaveBeenCalledTimes(1);
    });

    it('disables both buttons while an action is in flight', () => {
        renderPopup({disabled: true});
        expect(screen.getByRole('button', {name: 'Rotate'})).toBeDisabled();
        expect(screen.getByRole('button', {name: 'Remove'})).toBeDisabled();
    });

    it('uses the singular cell label for a size-1 ship', () => {
        renderPopup({shipTypeName: 'Patrol Boat', shipSize: 1});
        expect(screen.getByText('1 cell')).toBeInTheDocument();
    });
});
