import type {RefObject} from 'react';
import {useEffect, useRef} from 'react';

const FOCUSABLE_SELECTOR =
    'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Modal-dialog focus behavior, shared by any overlay that needs it (`ConfirmDialog`, `Sheet`):
 * while `open`, moves focus into `containerRef`'s subtree (first focusable element, or the
 * container itself if none), traps Tab/Shift+Tab within it, calls `onClose` on Escape, and
 * restores focus to whatever was focused before opening once `open` becomes false or the
 * component unmounts.
 */
export function useFocusTrap(containerRef: RefObject<HTMLElement | null>, open: boolean, onClose: () => void): void {
    const previouslyFocused = useRef<HTMLElement | null>(null);

    useEffect(() => {
        if (!open) {
            return;
        }

        previouslyFocused.current = document.activeElement as HTMLElement | null;

        const containerEl = containerRef.current;
        const focusables = containerEl
            ? Array.from(containerEl.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR))
            : [];
        (focusables[0] ?? containerEl)?.focus();

        function handleKeyDown(event: KeyboardEvent) {
            if (event.key === 'Escape') {
                event.preventDefault();
                onClose();
                return;
            }

            if (event.key !== 'Tab' || !containerEl) {
                return;
            }

            const currentFocusables = Array.from(
                containerEl.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR),
            );
            if (currentFocusables.length === 0) {
                event.preventDefault();
                return;
            }

            const first = currentFocusables[0];
            const last = currentFocusables[currentFocusables.length - 1];
            const active = document.activeElement;

            if (event.shiftKey) {
                if (active === first || !containerEl.contains(active)) {
                    event.preventDefault();
                    last.focus();
                }
            } else {
                if (active === last || !containerEl.contains(active)) {
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
    }, [containerRef, open, onClose]);
}
