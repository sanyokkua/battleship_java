import type {ReactNode} from 'react';
import {useEffect, useRef} from 'react';
import './ConfirmDialog.css';

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

const FOCUSABLE_SELECTOR =
    'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

const TITLE_ID = 'confirm-dialog-title';
const BODY_ID = 'confirm-dialog-body';

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
    const previouslyFocused = useRef<HTMLElement | null>(null);

    useEffect(() => {
        if (!open) {
            return;
        }

        previouslyFocused.current = document.activeElement as HTMLElement | null;

        const dialogEl = dialogRef.current;
        const focusables = dialogEl
            ? Array.from(dialogEl.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR))
            : [];
        (focusables[0] ?? dialogEl)?.focus();

        function handleKeyDown(event: KeyboardEvent) {
            if (event.key === 'Escape') {
                event.preventDefault();
                onCancel();
                return;
            }

            if (event.key !== 'Tab' || !dialogEl) {
                return;
            }

            const currentFocusables = Array.from(
                dialogEl.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR),
            );
            if (currentFocusables.length === 0) {
                event.preventDefault();
                return;
            }

            const first = currentFocusables[0];
            const last = currentFocusables[currentFocusables.length - 1];
            const active = document.activeElement;

            if (event.shiftKey) {
                if (active === first || !dialogEl.contains(active)) {
                    event.preventDefault();
                    last.focus();
                }
            } else {
                if (active === last || !dialogEl.contains(active)) {
                    event.preventDefault();
                    first.focus();
                }
            }
        }

        document.addEventListener('keydown', handleKeyDown, true);

        return () => {
            document.removeEventListener('keydown', handleKeyDown, true);
            previouslyFocused.current?.focus();
            previouslyFocused.current = null;
        };
    }, [open, onCancel]);

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
