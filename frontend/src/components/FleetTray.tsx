import type { PointerEvent } from 'react';
import type { components } from '../contract/generated/api';
import { useT } from '../i18n/useT';
import './FleetTray.css';

type Ship = components['schemas']['Ship'];
export function FleetTray({
    ships,
    onSelect,
    disabled,
    onShipPointerDown,
}: {
    ships: Ship[];
    onSelect: (ship: Ship) => void;
    disabled: boolean;
    onShipPointerDown?: (event: PointerEvent<HTMLButtonElement>, ship: Ship) => void;
}) {
    const t = useT();
    const lengths = [...new Set(ships.map((ship) => ship.length))].sort((a, b) => b - a);
    const placed = ships.filter((ship) => ship.cells.length > 0).length;
    return (
        <div className="fleet-inventory" aria-label={t('placement.remaining')}>
            {lengths.map((length) => {
                const unplaced = ships.filter((ship) => ship.length === length && ship.cells.length === 0);
                return (
                    <button
                        type="button"
                        className="inventory-ship"
                        key={length}
                        disabled={disabled || unplaced.length === 0}
                        aria-label={`${String(length)} ${t('placement.cells')}: ${String(unplaced.length)} ${t('placement.remainingCount')}`}
                        style={
                            !disabled && unplaced.length > 0 && onShipPointerDown ? { touchAction: 'none' } : undefined
                        }
                        onPointerDown={(event) => {
                            if (unplaced[0]) onShipPointerDown?.(event, unplaced[0]);
                        }}
                        onClick={() => {
                            const ship = unplaced[0];
                            if (ship) onSelect(ship);
                        }}
                    >
                        <span>
                            {length} {t('placement.cells')}
                        </span>
                        <span className="fleet-segments" aria-hidden="true">
                            {Array.from({ length }, (_, index) => (
                                <i key={index} />
                            ))}
                        </span>
                        <b>
                            {unplaced.length} {t('placement.ships')}
                        </b>
                    </button>
                );
            })}
            <div className="inventory-ship" aria-label={t('placement.progress')}>
                <span>{t('placement.fleetProgress')}</span>
                <b>
                    {placed} / {ships.length}
                </b>
                <span>
                    {ships.length - placed} {t('placement.remainingCount')}
                </span>
            </div>
        </div>
    );
}
