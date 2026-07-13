import type {ToastData} from './ToastContext';
import './Toast.css';

const VARIANT_ICON: Record<ToastData['variant'], string> = {
    ok: '✅',
    info: '⏳',
    warn: '🎯',
    err: '⚠️',
};

export function Toast({toast, onDismiss}: { toast: ToastData; onDismiss: (id: string) => void }) {
    return (
        <div className={`toast ${toast.variant}`}>
            <div className="ic" aria-hidden="true">
                {VARIANT_ICON[toast.variant]}
            </div>
            <div className="tx">
                <b>{toast.title}</b>
                {toast.message}
            </div>
            <button
                type="button"
                className="toast-dismiss"
                aria-label="Dismiss notification"
                onClick={() => onDismiss(toast.id)}
            >
                ✕
            </button>
        </div>
    );
}
