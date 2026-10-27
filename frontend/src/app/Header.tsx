import type { ReactNode } from 'react';
import { Button } from '../components/Button';
import { useT } from '../i18n/useT';

interface Props {
    title?: string;
    refresh?: ReactNode;
    onSettings: () => void;
    onRules: () => void;
}

export function Header({ title, refresh, onSettings, onRules }: Props) {
    const t = useT();
    return (
        <header className="app-bar">
            <a className="brand" href="/" aria-label={t('app.name')}>
                <span aria-hidden="true">⚓</span>
                <strong>{t('app.name')}</strong>
            </a>
            <div className="app-bar-title">{title}</div>
            <div className="app-bar-actions">
                {refresh}
                <Button variant="ghost" onClick={onRules}>
                    {t('home.rules')}
                </Button>
                <Button variant="ghost" aria-label={t('app.settings')} onClick={onSettings}>
                    <span aria-hidden="true">⚙</span>
                    <span className="button-label">{t('app.settings')}</span>
                </Button>
            </div>
        </header>
    );
}
