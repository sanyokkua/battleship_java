import type {ReactNode} from 'react';
import {useRef} from 'react';
import {useFocusTrap} from '../../design/hooks/useFocusTrap';
import './ConfirmDialog.css';

/** Props for {@link ConfirmDialog}. Rendering is entirely controlled via `open`. */
export type ConfirmDialogProps = {
    open: boolean;
    icon?: ReactNode;
    title: string;
    body: string;
    cancelLabel: string;
    confirmLabel: string;
    onCancel: () => void;
    onConfirm: () => void;
};

const TITLE_ID = 'confirm-dialog-title';
const BODY_ID = 'confirm-dialog-body';

/**
 * Modal confirm/cancel dialog with focus-trapping and Escape-to-cancel.
 *
 * Renders `null` when `open` is false (no exit animation to wait for). Focus-trap/Escape/
 * restore-focus behavior is shared with other overlays via {@link useFocusTrap} — see
 * `role="dialog"` + `aria-modal` below for the rest of the a11y wiring. Clicking the backdrop
 * itself (not its children) triggers `onCancel`, matching the Escape-key behavior.
 */
export function ConfirmDialog({
                                  open,
                                  icon,
                                  title,
                                  body,
                                  cancelLabel,
                                  confirmLabel,
                                  onCancel,
                                  onConfirm,
                              }: ConfirmDialogProps) {
    const dialogRef = useRef<HTMLDivElement>(null);
    useFocusTrap(dialogRef, open, onCancel);

    if (!open) {
        return null;
    }

    function handleBackdropClick(event: React.MouseEvent<HTMLDivElement>) {
        if (event.target === event.currentTarget) {
            onCancel();
        }
    }

    return (
        <div className="dialog-backdrop" onClick={handleBackdropClick}>
            <div
                className="dialog"
                ref={dialogRef}
                role="dialog"
                aria-modal="true"
                aria-labelledby={TITLE_ID}
                aria-describedby={BODY_ID}
                tabIndex={-1}
            >
                {icon !== undefined && (
                    <div className="dic" aria-hidden="true">
                        {icon}
                    </div>
                )}
                <h3 id={TITLE_ID}>{title}</h3>
                <p id={BODY_ID}>{body}</p>
                <div className="dialog-actions">
                    <button type="button" className="btn btn-ghost" onClick={onCancel}>
                        {cancelLabel}
                    </button>
                    <button type="button" className="btn btn-danger" onClick={onConfirm}>
                        {confirmLabel}
                    </button>
                </div>
            </div>
        </div>
    );
}
