import { useEffect, useState } from 'react';
import { Button } from './Button';
import { useT } from '../i18n/useT';
import type { PwaGateway } from '../pwa/pwaGateway';

export function UpdatePrompt({ gateway }: { gateway: PwaGateway }) {
    const [available, setAvailable] = useState(false);
    const t = useT();

    useEffect(() => {
        gateway.onUpdateReady(() => {
            setAvailable(true);
        });
    }, [gateway]);

    if (!available) return null;
    return (
        <div className="update-prompt" role="status" aria-live="polite">
            <span>{t('pwa.updateAvailable')}</span>
            <Button
                variant="primary"
                onClick={() => {
                    void gateway.applyUpdate();
                }}
            >
                {t('pwa.updateNow')}
            </Button>
            <Button
                onClick={() => {
                    setAvailable(false);
                }}
            >
                {t('common.dismiss')}
            </Button>
        </div>
    );
}
