import { useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import type { components } from '../contract/generated/api';
import { columnLabel } from '../components/Board';
import { useT } from '../i18n/useT';
import { canPlace } from './placementRules';

type Ship = components['schemas']['Ship'];
type Coordinate = components['schemas']['Coordinate'];
type Command = components['schemas']['Command'];
type Orientation = components['schemas']['Orientation'];
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
    dimensions,
    shipsMayTouch,
    anchorElement,
}: Readonly<{
    open: boolean;
    anchor: Coordinate;
    ships: Ship[];
    selectedShip: Ship | undefined;
    editing: boolean;
    allowed: components['schemas']['Action'][];
    onClose: () => void;
    onPickAnchor: (ship: Ship) => void;
    onCommand: (command: Command) => void;
    dimensions: components['schemas']['Ruleset']['board'];
    shipsMayTouch: boolean;
    anchorElement?: HTMLElement | null | undefined;
}>) {
    const t = useT();
    const ref = useRef<HTMLDialogElement>(null);
    const [position, setPosition] = useState({ left: 8, top: 8 });
    useLayoutEffect(() => {
        if (!open) return;
        const panel = ref.current;
        if (!panel) return;
        const opener = anchorElement ?? (document.activeElement instanceof HTMLElement ? document.activeElement : null);
        const place = () => {
            const rect = opener?.getBoundingClientRect();
            const w = panel.offsetWidth,
                h = panel.offsetHeight;
            const board = opener?.closest('.board-panel')?.getBoundingClientRect();
            const actions = opener
                ?.closest('.placement-screen')
                ?.querySelector('.placement-actions')
                ?.getBoundingClientRect();
            let x = 8;
            if (rect) {
                const right = rect.right + 6;
                const left = rect.left - w - 6;
                if (board && right + w <= board.right - 8) x = right;
                else if (board && left >= board.left + 8) x = left;
                else x = right + w <= window.innerWidth - 8 ? right : left;
            }
            x = Math.max(8, Math.min(x, window.innerWidth - w - 8));
            let y = Math.max(8, Math.min(rect?.top ?? 8, window.innerHeight - h - 8));
            if (actions && x < actions.right && x + w > actions.left && y < actions.bottom && y + h > actions.top) {
                const above = actions.top - h - 6;
                const below = actions.bottom + 6;
                if (above >= 8) y = above;
                else if (below + h <= window.innerHeight - 8) y = below;
            }
            setPosition({ left: x, top: y });
        };
        place();
        panel.querySelector<HTMLButtonElement>('button')?.focus({ preventScroll: true });
        const key = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                event.preventDefault();
                onClose();
                opener?.focus();
            }
        };
        const outside = (event: PointerEvent) => {
            if (!panel.contains(event.target as Node) && event.target !== opener) onClose();
        };
        document.addEventListener('keydown', key);
        document.addEventListener('pointerdown', outside);
        window.addEventListener('resize', place);
        window.addEventListener('scroll', place, true);
        return () => {
            document.removeEventListener('keydown', key);
            document.removeEventListener('pointerdown', outside);
            window.removeEventListener('resize', place);
            window.removeEventListener('scroll', place, true);
        };
    }, [open, anchorElement, onClose]);
    if (!open) return null;
    const valid = (ship: Ship, direction: Orientation) =>
        canPlace(ship, anchor, direction, ships, dimensions, shipsMayTouch);
    const options = selectedShip
        ? [selectedShip]
        : [...new Map(ships.filter((s) => s.cells.length === 0).map((s) => [s.length, s])).values()].sort(
              (a, b) => b.length - a.length,
          );
    const rotated: Orientation = selectedShip?.orientation === 'VERTICAL' ? 'HORIZONTAL' : 'VERTICAL';
    const rotationAnchor = selectedShip?.anchor ?? anchor;
    const place = (ship: Ship, orientation: Orientation) => {
        onCommand({ type: 'PLACE_SHIP', shipId: ship.shipId, anchor, orientation });
    };
    return createPortal(
        <dialog
            ref={ref}
            open
            aria-label={`${t(editing ? 'placement.editShip' : 'placement.placeShip')} ${columnLabel(anchor.columnIndex)}${String(anchor.rowIndex + 1)}`}
            className="placement-popover"
            style={{
                position: 'fixed',
                left: position.left,
                top: position.top,
                right: 'auto',
                bottom: 'auto',
                margin: 0,
            }}
        >
            <h2>
                {t(editing ? 'placement.editShip' : 'placement.placeShip')} {columnLabel(anchor.columnIndex)}
                {anchor.rowIndex + 1}
                {selectedShip && !editing && (
                    <span className="fleet-segments" aria-hidden="true">
                        {Array.from({ length: selectedShip.length }, (_, i) => (
                            <i key={i} />
                        ))}
                    </span>
                )}
            </h2>
            {editing && selectedShip ? (
                <>
                    <span className="fleet-segments" aria-hidden="true">
                        {Array.from({ length: selectedShip.length }, (_, i) => (
                            <i key={i} />
                        ))}
                    </span>
                    {allowed.includes('PLACE_SHIP') && (
                        <button
                            type="button"
                            onClick={() => {
                                anchorElement?.focus();
                                onPickAnchor(selectedShip);
                            }}
                        >
                            {t('placement.move')}
                        </button>
                    )}
                    {allowed.includes('PLACE_SHIP') &&
                        selectedShip.length > 1 &&
                        canPlace(selectedShip, rotationAnchor, rotated, ships, dimensions, shipsMayTouch) && (
                            <button
                                type="button"
                                onClick={() => {
                                    onCommand({
                                        type: 'PLACE_SHIP',
                                        shipId: selectedShip.shipId,
                                        anchor: rotationAnchor,
                                        orientation: rotated,
                                    });
                                }}
                            >
                                {t('placement.rotate')}
                            </button>
                        )}
                    {allowed.includes('REMOVE_SHIP') && (
                        <button
                            type="button"
                            onClick={() => {
                                onCommand({ type: 'REMOVE_SHIP', shipId: selectedShip.shipId });
                            }}
                        >
                            {t('placement.remove')}
                        </button>
                    )}
                </>
            ) : (
                <>
                    {options.map((ship) => {
                        const directions: Orientation[] = (
                            ship.length === 1
                                ? (['HORIZONTAL'] as Orientation[])
                                : (['HORIZONTAL', 'VERTICAL'] as Orientation[])
                        ).filter((direction) => valid(ship, direction));
                        if (!directions.length) return null;
                        const count = ships.filter((s) => s.length === ship.length && s.cells.length === 0).length;
                        return (
                            <div className="placement-popover-row" key={ship.length}>
                                <span className="placement-popover-info">
                                    {!selectedShip && (
                                        <>
                                            <span className="fleet-segments" aria-hidden="true">
                                                {Array.from({ length: ship.length }, (_, i) => (
                                                    <i key={i} />
                                                ))}
                                            </span>
                                            <span>× {count}</span>
                                        </>
                                    )}
                                </span>
                                <span className="placement-popover-dirs">
                                    {directions.map((direction) => {
                                        const directionLabel =
                                            ship.length === 1
                                                ? ''
                                                : t(
                                                      direction === 'HORIZONTAL'
                                                          ? 'placement.horizontal'
                                                          : 'placement.vertical',
                                                  );
                                        let mark = '✓';
                                        if (ship.length > 1) mark = direction === 'HORIZONTAL' ? '↔\uFE0E' : '↕\uFE0E';
                                        return (
                                            <button
                                                type="button"
                                                key={direction}
                                                className={
                                                    ship.length === 1
                                                        ? 'is-single'
                                                        : direction === 'HORIZONTAL'
                                                          ? 'is-horizontal'
                                                          : 'is-vertical'
                                                }
                                                aria-label={`${t('placement.placeShip')} ${String(ship.length)} ${t('placement.cells')} ${directionLabel}`}
                                                onClick={() => {
                                                    place(ship, direction);
                                                }}
                                            >
                                                {mark}
                                            </button>
                                        );
                                    })}
                                </span>
                            </div>
                        );
                    })}
                    {!options.some(
                        (ship) => valid(ship, 'HORIZONTAL') || (ship.length > 1 && valid(ship, 'VERTICAL')),
                    ) && <p>{t('placement.noFit')}</p>}
                </>
            )}
            <button
                type="button"
                onClick={() => {
                    onClose();
                    anchorElement?.focus();
                }}
            >
                {t('common.cancel')}
            </button>
        </dialog>,
        document.body,
    );
}
