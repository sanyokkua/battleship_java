import type {MouseEvent, ReactNode} from 'react';
import {useId, useRef} from 'react';
import {useFocusTrap} from '../../hooks/useFocusTrap';
import './Sheet.css';

/** Props for {@link Sheet}. Rendering is entirely controlled via `open`. */
export type SheetProps = {
    open: boolean;
    title: string;
    onClose: () => void;
    children: ReactNode;
};

/**
 * Generic overlay container for popups that need more than `ConfirmDialog`'s fixed
 * title/body/two-button shape — e.g. the ship-placement and rotate/remove popups. Centered
 * card on desktop, bottom sheet on mobile (`@media (max-width: 640px)`), sharing
 * `ConfirmDialog`'s focus-trap/Escape/backdrop-click/restore-focus behavior via
 * {@link useFocusTrap}. Content is supplied entirely via `children`.
 */
export function Sheet({open, title, onClose, children}: SheetProps) {
    const sheetRef = useRef<HTMLDivElement>(null);
    const titleId = useId();
    useFocusTrap(sheetRef, open, onClose);

    if (!open) {
        return null;
    }

    function handleBackdropClick(event: MouseEvent<HTMLDivElement>) {
        if (event.target === event.currentTarget) {
            onClose();
        }
    }

    return (
        <div className="sheet-backdrop" onClick={handleBackdropClick}>
            <div className="sheet" ref={sheetRef} role="dialog" aria-modal="true" aria-labelledby={titleId}
                 tabIndex={-1}>
                <h3 id={titleId} className="sheet-title">{title}</h3>
                <div className="sheet-content">{children}</div>
            </div>
        </div>
    );
}
