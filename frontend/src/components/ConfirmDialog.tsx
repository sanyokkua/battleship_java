import { useId } from 'react';
import { Button } from './Button';
import { Dialog } from './Dialog';
import { useT } from '../i18n/useT';

export function ConfirmDialog({
    open,
    title,
    message,
    confirmLabel,
    onConfirm,
    onClose,
    confirmDisabled = false,
}: {
    open: boolean;
    title: string;
    message: string;
    confirmLabel: string;
    onConfirm: () => void;
    onClose: () => void;
    confirmDisabled?: boolean;
}) {
    const t = useT();
    const titleId = useId();
    return (
        <Dialog open={open} titleId={titleId} onClose={onClose}>
            <div className="dialog-head">
                <h2 id={titleId}>{title}</h2>
            </div>
            <div className="dialog-body">
                <p>{message}</p>
            </div>
            <div className="dialog-actions">
                <Button onClick={onClose}>{t('common.cancel')}</Button>
                <Button variant="danger" disabled={confirmDisabled} onClick={onConfirm}>
                    {confirmLabel}
                </Button>
            </div>
        </Dialog>
    );
}
