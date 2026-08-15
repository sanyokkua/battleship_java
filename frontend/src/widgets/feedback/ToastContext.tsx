import type {ReactNode} from 'react';
import {createContext, useCallback, useContext, useMemo, useRef, useState} from 'react';

/** Visual/semantic category of a toast; drives icon and styling in {@link Toast}. */
export type ToastVariant = 'ok' | 'info' | 'warn' | 'err';

/** A single queued toast notification. */
export type ToastData = {
    id: string;
    variant: ToastVariant;
    title: string;
    message: string;
};

/** Shape of the context exposed by {@link ToastProvider} and read via {@link useToastContext}. */
export type ToastContextValue = {
    toasts: ToastData[];
    push: (toast: Omit<ToastData, 'id'>) => void;
    dismiss: (id: string) => void;
};

const ToastContext = createContext<ToastContextValue | undefined>(undefined);

/** Maximum number of toasts visible at once; a `push` beyond this evicts the oldest first (FIFO). */
const MAX_TOASTS = 3;

/**
 * Owns the live toast queue for its subtree.
 *
 * `id` is assigned here (an incrementing counter, not a UUID — toasts are ephemeral,
 * in-memory, and never persisted or compared across sessions, so uniqueness within
 * a page lifetime is all that's needed). Consumers add toasts via `push` (id omitted,
 * assigned internally) and remove them via `dismiss` (by id); typically read through
 * {@link useNotify} rather than this context directly. At most {@link MAX_TOASTS} toasts
 * are ever live at once — a `push` beyond that cap drops the oldest toast first (FIFO).
 */
export function ToastProvider({children}: { children: ReactNode }) {
    const [toasts, setToasts] = useState<ToastData[]>([]);
    const nextId = useRef(0);

    const push = useCallback((toast: Omit<ToastData, 'id'>) => {
        const id = String(nextId.current++);
        setToasts((prev) => {
            const next = [...prev, {...toast, id}];
            return next.length > MAX_TOASTS ? next.slice(next.length - MAX_TOASTS) : next;
        });
    }, []);

    const dismiss = useCallback((id: string) => {
        setToasts((prev) => prev.filter((toast) => toast.id !== id));
    }, []);

    const value = useMemo<ToastContextValue>(() => ({toasts, push, dismiss}), [toasts, push, dismiss]);

    return <ToastContext.Provider value={value}>{children}</ToastContext.Provider>;
}

/**
 * Reads the nearest {@link ToastProvider}'s context.
 *
 * @throws Error if called outside a `ToastProvider`.
 */
export function useToastContext(): ToastContextValue {
    const ctx = useContext(ToastContext);
    if (!ctx) {
        throw new Error('useToastContext must be used within a ToastProvider');
    }
    return ctx;
}
