import { useT } from '../i18n/useT';

export function OfflineBanner({ online }: Readonly<{ online: boolean }>) {
    const t = useT();

    if (online) return null;
    return (
        <p className="offline-banner" role="status">
            {t('pwa.networkRequired')}
        </p>
    );
}
