import { useT } from '../i18n/useT';
import { Pill } from './Pill';

export function ConnectionStatus({
    connection,
    replaced = false,
}: {
    readonly connection: 'connected' | 'reconnecting' | 'offline';
    readonly replaced?: boolean;
}) {
    const t = useT();
    if (replaced)
        return (
            <Pill role="status" tone="warning">
                {t('connection.replaced')}
            </Pill>
        );
    const tone = connection === 'connected' ? 'good' : 'warning';
    let messageKey = 'connection.reconnectingShort';
    if (connection === 'connected') messageKey = 'connection.connected';
    else if (connection === 'offline') messageKey = 'connection.offlineShort';
    return <Pill tone={tone}>{t(messageKey)}</Pill>;
}
