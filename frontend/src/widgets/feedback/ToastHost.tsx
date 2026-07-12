import type { ReactNode } from 'react';
import type { ToastData } from './ToastContext';

/**
 * ARIA wiring for a single toast.
 *
 * Toasts have mixed urgency (ok/info/warn are ambient, err is important), but a
 * single wrapping live region can't easily carry two different politeness levels
 * per item. Rather than stand up two separate `aria-live` regions (one polite, one
 * assertive) and route toasts between them, each toast gets its own implicit live
 * region via role: `role="status"` (implicit `aria-live="polite"`) for ok/info/warn,
 * `role="alert"` (implicit `aria-live="assertive"`) for err. This is simpler, needs
 * no wrapping region, and is a well-supported pattern for toast/notification lists.
 */
export function toastAriaRole(variant: ToastData['variant']): 'status' | 'alert' {
  return variant === 'err' ? 'alert' : 'status';
}

export function ToastHost({ toast, children }: { toast: ToastData; children: ReactNode }) {
  return (
    <div role={toastAriaRole(toast.variant)}>
      {children}
    </div>
  );
}
