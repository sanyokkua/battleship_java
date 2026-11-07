import { useEffect, useRef, useState, type PointerEvent, type MouseEvent } from 'react';
import type { components } from '../contract/generated/api';

type Ship = components['schemas']['Ship'];
type Coordinate = components['schemas']['Coordinate'];
type Orientation = components['schemas']['Orientation'];
interface Drag {
    pointerId: number;
    source: HTMLButtonElement;
    ship: Ship;
    orientation: Orientation;
    offset: Coordinate;
    x: number;
    y: number;
    moved: boolean;
}

export function usePlacementDrag({
    dimensions,
    disabled,
    onPlace,
}: {
    dimensions: components['schemas']['Ruleset']['board'];
    disabled: boolean;
    onPlace: (ship: Ship, orientation: Orientation, anchor: Coordinate) => void;
}) {
    const boardRef = useRef<HTMLDivElement>(null);
    const active = useRef<Drag | undefined>(undefined);
    const suppressClick = useRef(false);
    const [preview, setPreview] = useState<Coordinate>();
    const [draggingPlacedShip, setDraggingPlacedShip] = useState<Ship>();
    const release = () => {
        const drag = active.current;
        active.current = undefined;
        setDraggingPlacedShip(undefined);
        if (drag?.source.hasPointerCapture(drag.pointerId)) drag.source.releasePointerCapture(drag.pointerId);
    };
    useEffect(() => {
        if (disabled) return;
        return () => {
            release();
            setPreview(undefined);
        };
    }, [disabled]);
    const anchorAt = (x: number, y: number, drag: Drag): Coordinate | undefined => {
        const cells = boardRef.current?.querySelectorAll('[role="gridcell"]');
        const first = cells?.[0]?.getBoundingClientRect();
        const last = cells?.[cells.length - 1]?.getBoundingClientRect();
        if (!first || !last) return;
        const row = Math.floor((y - first.top) / ((last.bottom - first.top) / dimensions.rows)) - drag.offset.rowIndex;
        const column =
            Math.floor((x - first.left) / ((last.right - first.left) / dimensions.columns)) - drag.offset.columnIndex;
        if (
            x < first.left ||
            x > last.right ||
            y < first.top ||
            y > last.bottom ||
            row < 0 ||
            column < 0 ||
            row >= dimensions.rows ||
            column >= dimensions.columns
        )
            return;
        return { rowIndex: row, columnIndex: column };
    };
    const start = (
        event: PointerEvent<HTMLButtonElement>,
        ship: Ship,
        orientation: Orientation,
        grabbed?: Coordinate,
    ) => {
        if (disabled || active.current || !event.isPrimary || event.button !== 0 || event.pointerType === 'touch')
            return;
        suppressClick.current = false;
        active.current = {
            pointerId: event.pointerId,
            source: event.currentTarget,
            ship,
            orientation,
            offset: {
                rowIndex: grabbed && ship.anchor ? grabbed.rowIndex - ship.anchor.rowIndex : 0,
                columnIndex: grabbed && ship.anchor ? grabbed.columnIndex - ship.anchor.columnIndex : 0,
            },
            x: event.clientX,
            y: event.clientY,
            moved: false,
        };
        event.currentTarget.setPointerCapture(event.pointerId);
    };
    const cancel = (event: PointerEvent) => {
        if (active.current?.pointerId !== event.pointerId) return;
        suppressClick.current = active.current.moved;
        release();
        setPreview(undefined);
    };
    return {
        boardRef,
        preview: disabled ? undefined : preview,
        draggingPlacedShip: disabled ? undefined : draggingPlacedShip,
        start,
        handlers: {
            onPointerDownCapture: () => {
                // A disabled source may not emit a click after the drop. Suppress only
                // the drag's click, never a later independent pointer interaction.
                if (!active.current) suppressClick.current = false;
            },
            onPointerMove: (event: PointerEvent) => {
                const drag = active.current;
                if (drag?.pointerId !== event.pointerId) return;
                if (disabled) {
                    cancel(event);
                    return;
                }
                if (Math.hypot(event.clientX - drag.x, event.clientY - drag.y) >= 5) drag.moved = true;
                if (drag.moved) {
                    if (drag.ship.cells.length) setDraggingPlacedShip(drag.ship);
                    setPreview(anchorAt(event.clientX, event.clientY, drag));
                }
            },
            onPointerUp: (event: PointerEvent) => {
                const drag = active.current;
                if (drag?.pointerId !== event.pointerId) return;
                const anchor = drag.moved && !disabled ? anchorAt(event.clientX, event.clientY, drag) : undefined;
                suppressClick.current = drag.moved;
                release();
                setPreview(undefined);
                if (anchor) onPlace(drag.ship, drag.orientation, anchor);
            },
            onPointerCancel: cancel,
            onLostPointerCapture: cancel,
            onClickCapture: (event: MouseEvent) => {
                if (!suppressClick.current || event.detail === 0) return;
                suppressClick.current = false;
                event.preventDefault();
                event.stopPropagation();
            },
        },
    };
}
