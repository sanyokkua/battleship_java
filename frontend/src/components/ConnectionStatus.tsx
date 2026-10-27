import { useT } from '../i18n/useT';
import { Pill } from './Pill';

export function ConnectionStatus({
    connection,
    replaced = false,
}: {
    connection: 'connected' | 'reconnecting' | 'offline';
    replaced?: boolean;
}) {
    const t = useT();
    if (replaced)
        return (
            <Pill role="status" tone="warning">
                {t('connection.replaced')}
            </Pill>
        );
    const tone = connection === 'connected' ? 'good' : 'warning';
    return (
        <Pill tone={tone}>
            {t(
                connection === 'connected'
                    ? 'connection.connected'
                    : connection === 'offline'
                      ? 'connection.offlineShort'
                      : 'connection.reconnectingShort',
            )}
        </Pill>
    );
}
