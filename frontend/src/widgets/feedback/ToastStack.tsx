import {useEffect, useRef} from 'react';
import {Toast} from './Toast';
import {ToastHost} from './ToastHost';
import {useToastContext} from './ToastContext';
import './ToastStack.css';

const AUTO_DISMISS_MS = 4000;

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
