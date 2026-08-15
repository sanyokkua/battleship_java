import {useState} from 'react';
import type {ShipDirection} from '../../logic/ApplicationTypes';
import {Button} from '../../design/components/Button/Button';
import {Sheet} from '../../design/components/Sheet/Sheet';
import './ShipPlacementPopup.css';

/**
 * One group of remaining, interchangeable same-size ships that fit at the tapped cell, paired
 * with which orientation(s) work there. `shipId` is a *representative* id — any ship in the
 * group is equivalent (see {@link computeGroupedEligibleShips} in `preparationPlacement.ts`), so
 * placing this id is exactly as correct as placing any other member of the group.
 */
export type ShipPlacementOption = {
    shipId: string;
    shipSize: number;
    typeName: string; // already-localized ship type name (e.g. "Carrier")
    count: number; // how many interchangeable ships this row represents
    directions: ShipDirection[];
};

export type ShipPlacementPopupProps = {
    open: boolean;
    options: ShipPlacementOption[];
    /** True while a placement is in flight; disables all buttons to prevent double-submit. */
    disabled?: boolean;
    onClose: () => void;
    onConfirm: (shipId: string, direction: ShipDirection) => void;
    pickShipTitle: string;
    pickDirectionTitle: string;
    emptyStateMessage: string;
    closeLabel: string;
    backLabel: string;
    horizontalLabel: string;
    verticalLabel: string;
    cellSingularLabel: string;
    cellPluralLabel: string;
    /** Plain word (e.g. "available") composed with a group's count, e.g. "4 available". */
    availableLabel: string;
};

/**
 * Tap-empty-cell placement popup: step 1 lists remaining ships that fit at the tapped cell
 * (pre-filtered by the caller via `computeEligibleShips`), step 2 lists only that ship's valid
 * orientations there (pre-filtered via `computeValidDirections`). Confirming a direction calls
 * `onConfirm`; the caller owns the actual `placeShip` adapter call, toasts, and error handling.
 */
export function ShipPlacementPopup({
                                       open,
                                       options,
                                       disabled,
                                       onClose,
                                       onConfirm,
                                       pickShipTitle,
                                       pickDirectionTitle,
                                       emptyStateMessage,
                                       closeLabel,
                                       backLabel,
                                       horizontalLabel,
                                       verticalLabel,
                                       cellSingularLabel,
                                       cellPluralLabel,
                                       availableLabel,
                                   }: ShipPlacementPopupProps) {
    const [selectedShipId, setSelectedShipId] = useState<string | null>(null);

    // Reset to the ship-picking step whenever the popup closes, so reopening — even at the
    // same cell — never skips straight to a stale direction step. Adjusting state during
    // render (rather than in an effect) on a prop transition is the React-recommended
    // pattern for this; see https://react.dev/learn/you-might-not-need-an-effect.
    const [prevOpen, setPrevOpen] = useState(open);
    if (open !== prevOpen) {
        setPrevOpen(open);
        if (!open) {
            setSelectedShipId(null);
        }
    }

    const selected = options.find((o) => o.shipId === selectedShipId) ?? null;

    return (
        <Sheet open={open} title={selected ? pickDirectionTitle : pickShipTitle} onClose={onClose}>
            {selected ? (
                <div className="ship-placement-directions">
                    {selected.directions.map((dir) => (
                        <Button
                            key={dir}
                            variant="primary"
                            disabled={disabled}
                            onClick={() => onConfirm(selected.shipId, dir)}
                        >
                            {dir === 'HORIZONTAL' ? horizontalLabel : verticalLabel}
                        </Button>
                    ))}
                    <Button variant="ghost" disabled={disabled} onClick={() => setSelectedShipId(null)}>
                        {backLabel}
                    </Button>
                </div>
            ) : options.length === 0 ? (
                <div className="ship-placement-empty">
                    <p>{emptyStateMessage}</p>
                    <Button variant="ghost" onClick={onClose}>
                        {closeLabel}
                    </Button>
                </div>
            ) : (
                <div className="ship-placement-ships">
                    {options.map((option) => (
                        <button
                            key={option.shipId}
                            type="button"
                            className="ship-placement-option"
                            disabled={disabled}
                            onClick={() => {
                                // A 1-cell ship's footprint is identical either way (computeShipCells
                                // produces the same single coordinate for both directions when
                                // size === 1), so a direction step here would be pure noise — confirm
                                // immediately instead. 'HORIZONTAL' is an arbitrary but always-valid
                                // choice for this case.
                                if (option.shipSize === 1) {
                                    onConfirm(option.shipId, 'HORIZONTAL');
                                } else {
                                    setSelectedShipId(option.shipId);
                                }
                            }}
                        >
                            <span className="ship-info">
                                <span className="nm">{option.typeName}</span>{' '}
                                <small>
                                    {option.shipSize} {option.shipSize === 1 ? cellSingularLabel : cellPluralLabel}
                                </small>
                            </span>{' '}
                            <span className="count-badge">
                                {option.count} {availableLabel}
                            </span>
                        </button>
                    ))}
                </div>
            )}
        </Sheet>
    );
}
