import { useEffect, useRef, type ReactNode } from 'react';
import './Dialog.css';

interface Props {
    open: boolean;
    titleId: string;
    onClose: () => void;
    children: ReactNode;
    dismissible?: boolean;
}

export function Dialog({ open, titleId, onClose, children, dismissible = true }: Readonly<Props>) {
    const ref = useRef<HTMLDialogElement>(null);
    const opener = useRef<HTMLElement | null>(null);
    useEffect(() => {
        const dialog = ref.current;
        if (!dialog) return;
        if (open && !dialog.open) {
            opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
            dialog.showModal();
            dialog
                .querySelector<HTMLElement>(
                    'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href]',
                )
                ?.focus();
        } else if (!open && dialog.open) {
            dialog.close();
            opener.current?.focus();
        }
    }, [open]);
    return (
        <dialog
            ref={ref}
            className="dialog"
            aria-labelledby={titleId}
            tabIndex={-1}
            onCancel={(event) => {
                if (!dismissible) event.preventDefault();
                else onClose();
            }}
            onKeyDown={(event) => {
                if (event.key !== 'Tab') return;
                const dialog = ref.current;
                if (!dialog) return;
                const focusable = Array.from(
                    dialog.querySelectorAll<HTMLElement>(
                        'a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
                    ),
                );
                const first = focusable.at(0) ?? dialog;
                const last = focusable.at(-1) ?? dialog;
                if (!dialog.contains(document.activeElement)) {
                    event.preventDefault();
                    (event.shiftKey ? last : first).focus();
                } else if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog)) {
                    event.preventDefault();
                    last.focus();
                } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialog)) {
                    event.preventDefault();
                    first.focus();
                }
            }}
        >
            {children}
        </dialog>
    );
}
