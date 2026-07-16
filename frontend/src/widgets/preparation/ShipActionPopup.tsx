import {Button} from '../../design/components/Button/Button';
import {Sheet} from '../../design/components/Sheet/Sheet';
import './ShipActionPopup.css';

export type ShipActionPopupProps = {
    open: boolean;
    shipTypeName: string;
    shipSize: number;
    /** Whether rotating this ship in place is geometrically valid — hides Rotate when false. */
    canRotate: boolean;
    /** True while a rotate/remove action is in flight; disables both buttons to prevent double-submit. */
    disabled?: boolean;
    onClose: () => void;
    onRotate: () => void;
    onRemove: () => void;
    rotateLabel: string;
    removeLabel: string;
    cellSingularLabel: string;
    cellPluralLabel: string;
};

/**
 * Tap-placed-ship popup: Rotate (only offered when `canRotate` — the caller computes this via
 * `computeValidRotation`) and Remove. The caller owns the actual `removeShipAt`/`placeShip`
 * adapter calls, toasts, and error handling — this widget only bubbles plain callbacks up,
 * matching `ShipTray`/`DirectionToggle`'s convention.
 */
export function ShipActionPopup({
                                    open,
                                    shipTypeName,
                                    shipSize,
                                    canRotate,
                                    disabled,
                                    onClose,
                                    onRotate,
                                    onRemove,
                                    rotateLabel,
                                    removeLabel,
                                    cellSingularLabel,
                                    cellPluralLabel,
                                }: ShipActionPopupProps) {
    return (
        <Sheet open={open} title={shipTypeName} onClose={onClose}>
            <p className="ship-action-size">
                {shipSize} {shipSize === 1 ? cellSingularLabel : cellPluralLabel}
            </p>
            <div className="ship-action-buttons">
                {canRotate && (
                    <Button variant="primary" disabled={disabled} onClick={onRotate}>
                        {rotateLabel}
                    </Button>
                )}
                <Button variant="danger" disabled={disabled} onClick={onRemove}>
                    {removeLabel}
                </Button>
            </div>
        </Sheet>
    );
}
