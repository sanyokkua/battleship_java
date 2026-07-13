import {useTranslation} from 'react-i18next';
import type {ToastVariant} from './ToastContext';
import {useToastContext} from './ToastContext';

export type NotifyKindHandler = (key: string, params?: Record<string, unknown>) => void;

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
