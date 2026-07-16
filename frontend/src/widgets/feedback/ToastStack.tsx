import {useEffect, useRef} from 'react';
import {Toast} from './Toast';
import {ToastHost} from './ToastHost';
import {useToastContext} from './ToastContext';
import './ToastStack.css';

const AUTO_DISMISS_MS = 4000;

/**
 * Renders the live toast queue from {@link useToastContext} and auto-dismisses
 * each toast after `AUTO_DISMISS_MS`.
 *
 * Timers are tracked in a `Map` keyed by toast id (a ref, not state, since timer
 * handles are an imperative side effect that shouldn't trigger re-renders): the
 * first effect starts a timer for any newly-added toast and clears timers for
 * toasts no longer in the list (dismissed some other way, e.g. by the user), and
 * the second effect clears every outstanding timer on unmount. Renders `null`
 * when the queue is empty, so it's safe to mount unconditionally.
 */
export function ToastStack() {
    const {toasts, dismiss} = useToastContext();
    const timers = useRef(new Map<string, ReturnType<typeof setTimeout>>());

    useEffect(() => {
        const activeIds = new Set(toasts.map((toast) => toast.id));

        toasts.forEach((toast) => {
            if (!timers.current.has(toast.id)) {
                const timer = setTimeout(() => {
                    timers.current.delete(toast.id);
                    dismiss(toast.id);
                }, AUTO_DISMISS_MS);
                timers.current.set(toast.id, timer);
            }
        });

        timers.current.forEach((timer, id) => {
            if (!activeIds.has(id)) {
                clearTimeout(timer);
                timers.current.delete(id);
            }
        });
    }, [toasts, dismiss]);

    useEffect(() => {
        const timersMap = timers.current;
        return () => {
            timersMap.forEach((timer) => clearTimeout(timer));
            timersMap.clear();
        };
    }, []);

    if (toasts.length === 0) {
        return null;
    }

    return (
        <div className="toast-stack">
            {toasts.map((toast) => (
                <ToastHost key={toast.id} toast={toast}>
                    <Toast toast={toast} onDismiss={dismiss}/>
                </ToastHost>
            ))}
        </div>
    );
}
