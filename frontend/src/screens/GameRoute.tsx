import { useCallback, useEffect, useState } from 'react';
import { Button } from '../components/Button';
import { getMeta, getGame, listRulesets, GatewayError, type Ruleset } from '../gateway/gameGateway';
import { useGame } from '../session/useGame';
import { getGameStore, type GameId } from '../session/gameStore';
import { useT } from '../i18n/useT';
import { SessionUnavailable } from './SessionUnavailable';

export function GameRoute({ gameId, onTitle }: { gameId: GameId; onTitle: (title: string) => void }) {
    const t = useT();
    const state = useGame(gameId);
    const [rulesets, setRulesets] = useState<Ruleset[]>([]);
    const [failure, setFailure] = useState<number>();
    const [loading, setLoading] = useState(true);
    const refresh = useCallback(async () => {
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
        window.addEventListener('game-refresh', listener);
        return () => {
            window.removeEventListener('game-refresh', listener);
        };
    }, [refresh]);
    const snapshot = state.snapshot;
    const ruleset = rulesets.find((entry) => entry.id === snapshot?.rulesetId);
    useEffect(() => {
        if (ruleset) onTitle(t(`ruleset.${ruleset.id}`));
    }, [ruleset, t, onTitle]);
    if (failure === 401 || failure === 404 || failure === 410) return <SessionUnavailable expired={failure === 410} />;
    if (!snapshot && !loading && failure !== undefined) return <p role="alert">{t('problem.serviceUnavailable')}</p>;
    return (
        <section className="home-screen">
            <div className="game-card">
                <p role="status">{t(`game.${snapshot ? snapshot.phase.toLowerCase() : 'waiting'}`)}</p>
                {state.connection !== 'connected' && (
                    <p role="status">
                        {t(state.connection === 'offline' ? 'connection.offline' : 'connection.reconnecting')}
                    </p>
                )}
                <Button onClick={() => void refresh()}>{t('game.refresh')}</Button>
            </div>
        </section>
    );
}
