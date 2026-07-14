import {ShipItem, type ShipItemData} from './ShipItem';
import './ShipTray.css';

export type ShipTrayProps = {
    ships: ShipItemData[]; // full fleet, placed+unplaced, pre-merged by the screen
    activeShipId: string | null;
    onSelectShip: (shipId: string) => void;
    onRemoveShip: (shipId: string) => void; // screen resolves shipId -> a coordinate belonging to that ship before calling removeShipAt
    fleetLabel: string;
    hint: string;
    cellSingularLabel: string; // localized "cell"
    cellPluralLabel: string; // localized "cells"
    removeLabel: (typeName: string) => string; // builds a localized aria-label for a ship's remove button
};

/**
 * Fleet tray card: a label + hint line, then one `ShipItem` per fleet entry,
 * biggest ships first, with placed ships sorted before unplaced ones within
 * each same-size group.
 */
export function ShipTray({
                             ships,
                             activeShipId,
                             onSelectShip,
                             onRemoveShip,
                             fleetLabel,
                             hint,
                             cellSingularLabel,
                             cellPluralLabel,
                             removeLabel,
                         }: ShipTrayProps) {
    const sorted = [...ships].sort((a, b) => b.shipSize - a.shipSize || Number(b.placed) - Number(a.placed));

    return (
        <div className="fleet-panel card">
            <h4>{fleetLabel}</h4>
            <p className="hint">{hint}</p>
            {sorted.map((ship) => (
                <ShipItem
                    key={ship.shipId}
                    ship={{...ship, active: ship.shipId === activeShipId}}
                    cellLabel={`${ship.shipSize} ${ship.shipSize === 1 ? cellSingularLabel : cellPluralLabel}`}
                    removeLabel={removeLabel(ship.typeName)}
                    onSelect={() => onSelectShip(ship.shipId)}
                    onRemove={() => onRemoveShip(ship.shipId)}
                />
            ))}
        </div>
    );
}
