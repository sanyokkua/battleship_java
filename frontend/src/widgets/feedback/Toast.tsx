import type {ToastData} from './ToastContext';
import './Toast.css';

const VARIANT_ICON: Record<ToastData['variant'], string> = {
    ok: '✅',
    info: '⏳',
    warn: '🎯',
    err: '⚠️',
};

/**
 * Renders a single toast's content (icon, title, message, dismiss button).
 *
 * Presentational only — no timers, no ARIA live-region role (see {@link ToastHost}
 * for that) and no queue access; callers ({@link ToastStack}) own placement and
 * lifecycle, this just draws one toast and reports dismiss clicks by id.
 */
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
