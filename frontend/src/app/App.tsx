import { useEffect, useState, useSyncExternalStore } from 'react';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { useT } from '../i18n/useT';
import { preferenceStore, usePreferences } from '../preferences/preferences';
import { createThemeController } from '../preferences/themeController';
import { SettingsDialog } from '../screens/SettingsDialog';
import { getRoute, navigate } from './router';
import { Shell } from './Shell';

export function App() {
    const [settingsOpen, setSettingsOpen] = useState(false);
    const pathname = useSyncExternalStore(
        (notify) => {
            window.addEventListener('popstate', notify);
            return () => {
                window.removeEventListener('popstate', notify);
            };
        },
        () => window.location.pathname,
        () => '/',
    );
    const route = getRoute(pathname);
    const t = useT();
    const { locale } = usePreferences();
    useEffect(() => {
        const controller = createThemeController(preferenceStore, document.documentElement);
        return () => {
            controller.dispose();
        };
    }, []);
    useEffect(() => {
        document.documentElement.lang = locale;
    }, [locale]);
    return (
        <>
            <Shell
                onSettings={() => {
                    setSettingsOpen(true);
                }}
            >
                {route.name === 'home' ? (
                    <section className="home-screen">
                        <Card className="card-pad home-card">
                            <span className="eyebrow">{t('app.name')}</span>
                            <h1>{t('app.homeTitle')}</h1>
                            <Button
                                variant="primary"
                                onClick={() => {
                                    navigate('/create');
                                }}
                            >
                                {t('app.newGame')}
                            </Button>
                        </Card>
                    </section>
                ) : (
                    <section className="home-screen">
                        <Card className="card-pad">
                            <h1>{t('app.name')}</h1>
                            <Button
                                onClick={() => {
                                    navigate('/');
                                }}
                            >
                                {t('app.returnHome')}
                            </Button>
                        </Card>
                    </section>
                )}
            </Shell>
            <SettingsDialog
                open={settingsOpen}
                onClose={() => {
                    setSettingsOpen(false);
                }}
            />
        </>
    );
}
