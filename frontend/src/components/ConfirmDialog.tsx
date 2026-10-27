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
}: {
    open: boolean;
    title: string;
    message: string;
    confirmLabel: string;
    onConfirm: () => void;
    onClose: () => void;
}) {
    const t = useT();
    return (
        <Dialog open={open} titleId="confirm-title" onClose={onClose}>
            <div className="dialog-head">
                <h2 id="confirm-title">{title}</h2>
            </div>
            <div className="dialog-body">
                <p>{message}</p>
            </div>
            <div className="dialog-actions">
                <Button onClick={onClose}>{t('common.cancel')}</Button>
                <Button variant="danger" onClick={onConfirm}>
                    {confirmLabel}
                </Button>
            </div>
        </Dialog>
    );
}
