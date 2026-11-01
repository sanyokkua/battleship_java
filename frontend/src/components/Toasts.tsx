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
        const timer = window.setTimeout(onDismiss, 6500);
        return () => {
            window.clearTimeout(timer);
        };
    }, [toast, onDismiss]);
    return (
        <div className="toast-region" role="region" aria-label="Notifications" aria-live="polite" aria-atomic="true">
            {toast && (
                <div className={`toast toast-${toast.kind}`} role={toast.kind === 'error' ? 'alert' : 'status'}>
                    <span>{toast.message}</span>
                    <Button variant="ghost" aria-label={t('common.dismiss')} onClick={onDismiss}>
                        ×
                    </Button>
                </div>
            )}
        </div>
    );
}
