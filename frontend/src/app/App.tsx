import { useCallback, useEffect, useState, useSyncExternalStore } from 'react';
import { Button } from '../components/Button';
import { useT } from '../i18n/useT';
import { preferenceStore, usePreferences } from '../preferences/preferences';
import { createThemeController } from '../preferences/themeController';
import { CreateGame } from '../screens/CreateGame';
import { getMeta } from '../gateway/gameGateway';
import { GameRoute } from '../screens/GameRoute';
import { Home } from '../screens/Home';
import { RulesDialog } from '../screens/RulesDialog';
import { SettingsDialog } from '../screens/SettingsDialog';
import { SessionUnavailable } from '../screens/SessionUnavailable';
import { getRoute } from './router';
import { Shell } from './Shell';

export function App() {
    const [settingsOpen, setSettingsOpen] = useState(false);
    const [rulesOpen, setRulesOpen] = useState(false);
    const [gameTitle, setGameTitle] = useState('');
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
    const setTitle = useCallback((title: string) => {
        setGameTitle(title);
    }, []);
    useEffect(() => {
        const controller = createThemeController(preferenceStore, document.documentElement);
        return () => {
            controller.dispose();
        };
    }, []);
    useEffect(() => {
        document.documentElement.lang = locale;
    }, [locale]);
    useEffect(() => {
        void getMeta().catch(() => undefined);
    }, []);
    const title = route.name === 'game' ? gameTitle : undefined;
    const refresh =
        route.name === 'game' ? (
            <Button onClick={() => window.dispatchEvent(new CustomEvent('game-refresh'))}>{t('game.refresh')}</Button>
        ) : undefined;
    return (
        <>
            <Shell
                {...(title === undefined ? {} : { title })}
                {...(refresh === undefined ? {} : { refresh })}
                onRules={() => {
                    setRulesOpen(true);
                }}
                onSettings={() => {
                    setSettingsOpen(true);
                }}
            >
                {route.name === 'home' && (
                    <Home
                        onRules={() => {
                            setRulesOpen(true);
                        }}
                    />
                )}
                {route.name === 'create' && <CreateGame />}
                {route.name === 'game' && <GameRoute gameId={route.gameId} onTitle={setTitle} />}
                {route.name === 'join' && <SessionUnavailable expired={false} />}
            </Shell>
            <RulesDialog
                open={rulesOpen}
                onClose={() => {
                    setRulesOpen(false);
                }}
            />
            <SettingsDialog
                open={settingsOpen}
                onClose={() => {
                    setSettingsOpen(false);
                }}
            />
        </>
    );
}
