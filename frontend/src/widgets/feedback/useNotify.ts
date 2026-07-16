import {useTranslation} from 'react-i18next';
import type {ToastVariant} from './ToastContext';
import {useToastContext} from './ToastContext';

/**
 * Pushes a toast for one notification kind.
 *
 * `key` is looked up in the `notifications` i18n namespace as `${key}.title` and
 * `${key}.message` (e.g. `resolveErrorMessageKey`'s output is a typical `key` source
 * for the `error` handler). `params` is passed through to i18next interpolation.
 */
export type NotifyKindHandler = (key: string, params?: Record<string, unknown>) => void;

/** One handler per toast kind, returned by {@link useNotify}. */
export type Notify = {
    success: NotifyKindHandler;
    info: NotifyKindHandler;
    warn: NotifyKindHandler;
    error: NotifyKindHandler;
};

const KIND_TO_VARIANT: Record<keyof Notify, ToastVariant> = {
    success: 'ok',
    info: 'info',
    warn: 'warn',
    error: 'err',
};

/**
 * Convenience hook for firing toasts from the `notifications` i18n namespace.
 *
 * Wraps {@link useToastContext}'s `push`, translating a short `key` into a
 * title/message pair per kind so call sites don't touch `t()` or `ToastData`
 * directly (e.g. `notify.error('SESSION_NOT_FOUND')`).
 */
export function useNotify(): Notify {
    const {t} = useTranslation('notifications');
    const {push} = useToastContext();

    const notify = (kind: keyof Notify): NotifyKindHandler => {
        return (key: string, params?: Record<string, unknown>) => {
            const title = t(`${key}.title`, params ?? {});
            const message = t(`${key}.message`, params ?? {});
            push({variant: KIND_TO_VARIANT[kind], title, message});
        };
    };

    return {
        success: notify('success'),
        info: notify('info'),
        warn: notify('warn'),
        error: notify('error'),
    };
}
