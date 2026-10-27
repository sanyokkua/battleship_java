import { useState } from 'react';
import type { components } from '../contract/generated/api';
import { Dialog } from '../components/Dialog';
import { Button } from '../components/Button';
import { columnLabel } from '../components/Board';
import { useT } from '../i18n/useT';

type Ship = components['schemas']['Ship'];
type Coordinate = components['schemas']['Coordinate'];
type Command = components['schemas']['Command'];
export function ShipActionDialog({
    open,
    anchor,
    ships,
    selectedShip,
    editing,
    allowed,
    onClose,
    onPickAnchor,
    onCommand,
    onOrientationChange,
}: {
    open: boolean;
    anchor: Coordinate;
    ships: Ship[];
    selectedShip: Ship | undefined;
    editing: boolean;
    allowed: components['schemas']['Action'][];
    onClose: () => void;
    onPickAnchor: (ship: Ship) => void;
    onCommand: (command: Command) => void;
    onOrientationChange?: (orientation: components['schemas']['Orientation']) => void;
}) {
    const t = useT();
    const [shipId, setShipId] = useState(selectedShip?.shipId ?? ships.find((ship) => ship.cells.length === 0)?.shipId);
    const [orientation, setOrientation] = useState<components['schemas']['Orientation']>(
        selectedShip?.orientation ?? 'HORIZONTAL',
    );
    const eligible = ships.filter((entry) => entry.cells.length === 0 || entry.shipId === selectedShip?.shipId);
    const [wasOpen, setWasOpen] = useState(open);
    // Reset only on reopening; refreshed snapshots must not overwrite an active draft.
    if (open !== wasOpen) {
        setWasOpen(open);
        if (open) {
            setShipId(
                eligible.find((entry) => entry.shipId === selectedShip?.shipId)?.shipId ??
                    eligible.find((entry) => entry.cells.length === 0)?.shipId,
            );
            setOrientation(selectedShip?.orientation ?? 'HORIZONTAL');
        }
    }
    const ship = eligible.find((entry) => entry.shipId === shipId);
    // One chip per server ship type; repeated ships retain their server-issued ID.
    const optionsByType = new Map<string, Ship>();
    for (const entry of eligible) {
        if (!optionsByType.has(entry.shipTypeId) || entry.shipId === selectedShip?.shipId) {
            optionsByType.set(entry.shipTypeId, entry);
        }
    }
    const options = [...optionsByType.values()];
    const place = (direction = orientation) => {
        if (ship) onCommand({ type: 'PLACE_SHIP', shipId: ship.shipId, anchor, orientation: direction });
    };
    return (
        <Dialog open={open} titleId="ship-action-title" onClose={onClose}>
            <div className="dialog-head">
                <h2 id="ship-action-title">
                    {t(editing ? 'placement.editShip' : 'placement.placeShip')} {columnLabel(anchor.columnIndex)}
                    {anchor.rowIndex + 1}
                </h2>
            </div>
            <div className="dialog-body ship-options">
                {editing ? (
                    <div className="placement-actions">
                        {allowed.includes('PLACE_SHIP') && (
                            <>
                                <Button
                                    onClick={() => {
                                        if (ship) onPickAnchor({ ...ship, orientation });
                                    }}
                                >
                                    {t('placement.move')}
                                </Button>
                                <Button
                                    onClick={() => {
                                        place(orientation === 'HORIZONTAL' ? 'VERTICAL' : 'HORIZONTAL');
                                    }}
                                >
                                    {t('placement.rotate')}
                                </Button>
                            </>
                        )}
                        {allowed.includes('REMOVE_SHIP') && (
                            <Button
                                onClick={() => {
                                    if (ship) onCommand({ type: 'REMOVE_SHIP', shipId: ship.shipId });
                                }}
                            >
                                {t('placement.remove')}
                            </Button>
                        )}
                    </div>
                ) : (
                    <>
                        <div className="placement-actions" role="group" aria-label={t('placement.chooseShip')}>
                            {options.map((entry) => (
                                <Button
                                    key={entry.shipTypeId}
                                    aria-pressed={ship?.shipId === entry.shipId}
                                    onClick={() => {
                                        setShipId(entry.shipId);
                                    }}
                                >
                                    {t(`ship.${entry.shipTypeId}`)}
                                </Button>
                            ))}
                        </div>
                        <div className="placement-actions" role="group" aria-label={t('placement.direction')}>
                            {(['HORIZONTAL', 'VERTICAL'] as const).map((direction) => (
                                <Button
                                    key={direction}
                                    aria-pressed={orientation === direction}
                                    onClick={() => {
                                        setOrientation(direction);
                                        onOrientationChange?.(direction);
                                    }}
                                >
                                    {t(`placement.${direction.toLowerCase()}`)}
                                </Button>
                            ))}
                        </div>
                        <Button
                            disabled={!ship}
                            onClick={() => {
                                if (ship) onPickAnchor({ ...ship, orientation });
                            }}
                        >
                            {t('placement.chooseCell')}
                        </Button>
                    </>
                )}
            </div>
            <div className="dialog-actions">
                <Button onClick={onClose}>{t('common.cancel')}</Button>
                {!editing && allowed.includes('PLACE_SHIP') && (
                    <Button
                        variant="primary"
                        disabled={!ship}
                        onClick={() => {
                            place();
                        }}
                    >
                        {t('placement.apply')}
                    </Button>
                )}
            </div>
        </Dialog>
    );
}
