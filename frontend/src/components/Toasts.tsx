import { useEffect } from 'react';
import { Button } from './Button';
import { useT } from '../i18n/useT';
import './Toasts.css';

export interface ToastMessage {
    id: number;
    kind: 'success' | 'info' | 'warning' | 'error';
    message: string;
}

export function Toasts({ toast, onDismiss }: Readonly<{ toast: ToastMessage | undefined; onDismiss: () => void }>) {
    const t = useT();
    useEffect(() => {
        if (!toast) return;
        const timer = globalThis.setTimeout(onDismiss, 6500);
        return () => {
            globalThis.clearTimeout(timer);
        };
    }, [toast, onDismiss]);
    return (
        <section className="toast-region" aria-label={t('toast.region')} aria-live="polite" aria-atomic="true">
            {toast && (
                <output className={`toast toast-${toast.kind}`} role={toast.kind === 'error' ? 'alert' : undefined}>
                    <span>{toast.message}</span>
                    <Button variant="ghost" aria-label={t('common.dismiss')} onClick={onDismiss}>
                        ×
                    </Button>
                </output>
            )}
        </section>
    );
}
