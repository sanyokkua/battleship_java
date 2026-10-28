import { useCallback, useEffect, useState, useSyncExternalStore } from 'react';
import { Button } from '../components/Button';
import { OfflineBanner } from '../components/OfflineBanner';
import { UpdatePrompt } from '../components/UpdatePrompt';
import { useT } from '../i18n/useT';
import { preferenceStore, usePreferences } from '../preferences/preferences';
import { createThemeController } from '../preferences/themeController';
import { CreateGame } from '../screens/CreateGame';
import { getMeta } from '../gateway/gameGateway';
import { GameRoute } from '../screens/GameRoute';
import { Home } from '../screens/Home';
import { JoinInvitation } from '../screens/JoinInvitation';
import { RulesDialog } from '../screens/RulesDialog';
import { SettingsDialog } from '../screens/SettingsDialog';
import { getRoute } from './router';
import { Shell } from './Shell';
import { getGameStore } from '../session/gameStore';
import { useRetryWindow } from '../session/useRetryWindow';
import type { PwaGateway } from '../pwa/pwaGateway';
import type { InstallGateway } from '../pwa/installGateway';

export function App({ pwaGateway, installGateway }: { pwaGateway: PwaGateway; installGateway: InstallGateway }) {
    const [settingsOpen, setSettingsOpen] = useState(false);
    const [rulesOpen, setRulesOpen] = useState(false);
    const [gameTitle, setGameTitle] = useState('');
    const [installAvailable, setInstallAvailable] = useState(false);
    const [online, setOnline] = useState(() => navigator.onLine);
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
    const gameState = useSyncExternalStore(
        (notify) => (route.name === 'game' ? getGameStore(route.gameId).subscribe(notify) : () => undefined),
        () => (route.name === 'game' ? getGameStore(route.gameId).getSnapshot() : undefined),
        () => undefined,
    );
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
        const update = () => {
            setOnline(navigator.onLine);
        };
        window.addEventListener('online', update);
        window.addEventListener('offline', update);
        return () => {
            window.removeEventListener('online', update);
            window.removeEventListener('offline', update);
        };
    }, []);
    useEffect(() => {
        if (online) void getMeta().catch(() => undefined);
    }, [online]);
    useEffect(() => {
        installGateway.onAvailabilityChange(setInstallAvailable);
    }, [installGateway]);
    const title = route.name === 'game' ? gameTitle : undefined;
    const retryAtMs = gameState?.notice?.retryAtMs;
    const retryBlocked = useRetryWindow(retryAtMs);
    const refresh =
        route.name === 'game' ? (
            <Button
                disabled={gameState?.connection === 'offline' || retryBlocked}
                onClick={() => window.dispatchEvent(new CustomEvent('game-refresh'))}
            >
                {t('game.refresh')}
            </Button>
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
                {route.name !== 'game' && <OfflineBanner online={online} />}
                {route.name === 'home' && (
                    <Home
                        onRules={() => {
                            setRulesOpen(true);
                        }}
                    />
                )}
                {route.name === 'create' && <CreateGame offline={!online} />}
                {route.name === 'game' && (
                    <GameRoute
                        gameId={route.gameId}
                        onTitle={setTitle}
                        settingsOpen={settingsOpen}
                        installAvailable={installAvailable}
                        onInstall={() => {
                            void installGateway.prompt();
                        }}
                        onSettingsClose={() => {
                            setSettingsOpen(false);
                        }}
                    />
                )}
                {route.name === 'join' && <JoinInvitation gameId={route.gameId} offline={!online} />}
            </Shell>
            <UpdatePrompt gateway={pwaGateway} />
            <RulesDialog
                open={rulesOpen}
                onClose={() => {
                    setRulesOpen(false);
                }}
            />
            {route.name !== 'game' && (
                <SettingsDialog
                    open={settingsOpen}
                    installAvailable={installAvailable}
                    onInstall={() => {
                        void installGateway.prompt();
                    }}
                    onClose={() => {
                        setSettingsOpen(false);
                    }}
                />
            )}
        </>
    );
}
