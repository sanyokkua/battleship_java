import { useCallback, useEffect, useState, type ReactNode } from 'react';
import type { components } from '../contract/generated/api';
import { Dialog } from '../components/Dialog';
import { Button } from '../components/Button';
import { getMeta, getGame, listRulesets, GatewayError, sendCommand, type Ruleset } from '../gateway/gameGateway';
import { useGame } from '../session/useGame';
import { getGameStore, type GameId, type GameState } from '../session/gameStore';
import { useT } from '../i18n/useT';
import { SessionUnavailable } from './SessionUnavailable';
import { Gameplay } from './Gameplay';
import { Results } from './Results';
import { Placement } from './Placement';
import { WaitingRoom } from './WaitingRoom';
import { openGameEventStream } from '../gateway/eventStream';
import { startPresence } from '../session/presence';
import { LeaveControl } from './LeaveControl';
import { Abandoned } from './Abandoned';
import { SettingsDialog } from './SettingsDialog';
import { useRetryWindow } from '../session/useRetryWindow';

export function GameRoute({
    gameId,
    onTitle,
    settingsOpen,
    onSettingsClose,
    installAvailable,
    onInstall,
}: {
    gameId: GameId;
    onTitle: (title: string) => void;
    settingsOpen: boolean;
    onSettingsClose: () => void;
    installAvailable: boolean;
    onInstall: () => void;
}) {
    const t = useT();
    const state = useGame(gameId);
    const retryBlocked = useRetryWindow(state.notice?.retryAtMs);
    const [rulesets, setRulesets] = useState<Ruleset[]>([]);
    const [failure, setFailure] = useState<number>();
    const [loading, setLoading] = useState(true);
    const [streamReplaced, setStreamReplaced] = useState(false);
    const [showCreatedToast] = useState(() => {
        const historyState = window.history.state as { toast?: unknown } | null;
        return historyState?.toast === 'game-created';
    });
    const refresh = useCallback(async () => {
        const store = getGameStore(gameId);
        if (!navigator.onLine) {
            store.setConnection('offline');
            setFailure(undefined);
            setLoading(false);
            return;
        }
        const retryAtMs = getGameStore(gameId).getSnapshot().notice?.retryAtMs;
        if (retryAtMs !== undefined && retryAtMs > Date.now()) return;
        const [metaResult, rulesResult, gameResult] = await Promise.allSettled([
            getMeta(),
            listRulesets(),
            getGame(gameId),
        ]);
        if (metaResult.status === 'fulfilled') getGameStore(gameId).setMeta(metaResult.value);
        if (rulesResult.status === 'fulfilled') setRulesets(rulesResult.value);
        if (gameResult.status === 'fulfilled') {
            getGameStore(gameId).setConnection(
                metaResult.status === 'rejected' || rulesResult.status === 'rejected' ? 'reconnecting' : 'connected',
            );
            setFailure(undefined);
        } else {
            const error: unknown = gameResult.reason as unknown;
            const status = error instanceof GatewayError ? error.problem.status : 0;
            if (error instanceof GatewayError && [429, 503].includes(status)) {
                getGameStore(gameId).setNotice(error.problem);
            }
            const reconnectable = [0, 429, 502, 503].includes(status) && navigator.onLine;
            getGameStore(gameId).setConnection(reconnectable ? 'reconnecting' : 'offline');
            setFailure(status);
        }
        setLoading(false);
    }, [gameId]);
    useEffect(() => {
        const timer = window.setTimeout(() => {
            void refresh();
        }, 0);
        return () => {
            window.clearTimeout(timer);
        };
    }, [refresh]);
    useEffect(() => {
        const listener = () => void refresh();
        window.addEventListener('online', listener);
        return () => {
            window.removeEventListener('online', listener);
        };
    }, [refresh]);
    useEffect(() => {
        if (showCreatedToast) {
            window.history.replaceState(null, '', window.location.href);
        }
    }, [showCreatedToast]);
    useEffect(() => {
        getGameStore(gameId).setConnection('reconnecting');
        return openGameEventStream(gameId, setFailure, () => {
            setStreamReplaced(true);
        });
    }, [gameId]);
    useEffect(() => {
        const interval = state.meta?.limits.presenceIntervalSeconds;
        if (interval === undefined) return;
        return startPresence(gameId, interval);
    }, [gameId, state.meta]);
    useEffect(() => {
        const listener = () => void refresh();
        window.addEventListener('game-refresh', listener);
        return () => {
            window.removeEventListener('game-refresh', listener);
        };
    }, [refresh]);
    const send = async (request: components['schemas']['CommandRequest']) => {
        const store = getGameStore(gameId);
        try {
            await sendCommand(gameId, request, AbortSignal.timeout(10_000));
            store.settleAction();
        } catch (error) {
            if (error instanceof GatewayError) {
                store.settleAction(error.problem);
                if ([401, 404, 410].includes(error.problem.status)) void refresh();
            } else store.checkAction();
        }
    };
    const snapshot = state.snapshot;
    const ruleset = rulesets.find((entry) => entry.id === snapshot?.rulesetId);
    useEffect(() => {
        if (ruleset) onTitle(t(`ruleset.${ruleset.id}`));
    }, [ruleset, t, onTitle]);
    let content: ReactNode;
    if (failure === 401 || failure === 404 || failure === 410)
        content = <SessionUnavailable expired={failure === 410} />;
    else if (snapshot?.phase === 'PLAYING' && ruleset)
        content = <Gameplay gameId={gameId} ruleset={ruleset} streamReplaced={streamReplaced} onCommand={send} />;
    else if (snapshot?.phase === 'FINISHED' && ruleset)
        content = <Results gameId={gameId} ruleset={ruleset} streamReplaced={streamReplaced} />;
    else if (snapshot?.phase === 'ABANDONED') content = <Abandoned gameId={gameId} />;
    else if (snapshot?.phase === 'PLACEMENT' && ruleset)
        content = <Placement gameId={gameId} ruleset={ruleset} streamReplaced={streamReplaced} />;
    else if (snapshot?.phase === 'WAITING')
        content = (
            <WaitingRoom
                gameId={gameId}
                ruleset={ruleset}
                showCreatedToast={showCreatedToast}
                streamReplaced={streamReplaced}
            />
        );
    else if (!snapshot && !loading && failure !== undefined)
        content = <p role="alert">{t('problem.serviceUnavailable')}</p>;
    else
        content = (
            <section className="home-screen">
                <div className="game-card">
                    <p role="status">{t(`game.${snapshot ? snapshot.phase.toLowerCase() : 'waiting'}`)}</p>
                    {state.connection !== 'connected' && (
                        <p role="status">
                            {t(state.connection === 'offline' ? 'connection.offline' : 'connection.reconnecting')}
                        </p>
                    )}
                    <Button disabled={state.connection === 'offline' || retryBlocked} onClick={() => void refresh()}>
                        {t('game.refresh')}
                    </Button>
                </div>
            </section>
        );
    return (
        <>
            {(snapshot?.phase === 'WAITING' || snapshot?.phase === 'PLACEMENT') && <LeaveControl gameId={gameId} />}
            {state.notice && <CommandNotice notice={state.notice} />}
            {content}
            <Dialog open={state.checking} titleId="checking-title" dismissible={false} onClose={() => undefined}>
                <div className="dialog-head">
                    <h2 id="checking-title">{t('play.checkingTitle')}</h2>
                </div>
                <div className="dialog-body">
                    <p>{t('play.checkingHelp')}</p>
                </div>
                <div className="dialog-actions">
                    <Button
                        disabled={state.connection === 'offline' || streamReplaced}
                        onClick={() => {
                            const action = getGameStore(gameId).retryAction();
                            if (action) void send(action);
                        }}
                    >
                        {t('play.retry')}
                    </Button>
                </div>
            </Dialog>
            <SettingsDialog
                open={settingsOpen}
                installAvailable={installAvailable}
                onInstall={onInstall}
                canResign={snapshot?.allowedActions.includes('RESIGN') ?? false}
                resignDisabled={state.connection === 'offline'}
                onResign={async () => {
                    if (!navigator.onLine) return;
                    const action = getGameStore(gameId).beginAction({ type: 'RESIGN' });
                    if (action) await send(action);
                }}
                onClose={onSettingsClose}
            />
        </>
    );
}

function CommandNotice({ notice }: { notice: NonNullable<GameState['notice']> }) {
    const t = useT();
    const [now, setNow] = useState(Date.now);
    useEffect(() => {
        if (!notice.retryAtMs) return;
        const timer = window.setInterval(() => {
            setNow(Date.now());
        }, 1000);
        return () => {
            window.clearInterval(timer);
        };
    }, [notice.retryAtMs]);
    const waitSeconds = Math.max(0, Math.ceil(((notice.retryAtMs ?? 0) - now) / 1000));
    return (
        <p role="alert">
            {t(notice.messageKey)}{' '}
            {waitSeconds > 0 && t('problem.retryAfter').replace('{seconds}', String(waitSeconds))}
        </p>
    );
}
