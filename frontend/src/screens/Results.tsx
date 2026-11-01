import { useEffect, useState } from 'react';
import { navigate } from '../app/router';
import { Board } from '../components/Board';
import { Button } from '../components/Button';
import { StatCard } from '../components/StatCard';
import { GatewayError, leaveGame, type Ruleset } from '../gateway/gameGateway';
import type { GatewayProblem } from '../gateway/problems';
import { useT } from '../i18n/useT';
import type { GameId } from '../session/gameStore';
import { useGame } from '../session/useGame';
import './Results.css';

function duration(value: number | undefined): string {
    if (value === undefined) return '—';
    const seconds = Math.floor(value / 1000);
    return `${String(Math.floor(seconds / 60))}:${String(seconds % 60).padStart(2, '0')}`;
}

export function Results({
    gameId,
    ruleset,
    streamReplaced,
}: Readonly<{
    gameId: GameId;
    ruleset: Ruleset;
    streamReplaced: boolean;
}>) {
    const t = useT();
    const state = useGame(gameId);
    const [now, setNow] = useState(Date.now);
    const [leaving, setLeaving] = useState(false);
    const [failure, setFailure] = useState<GatewayProblem>();
    const [retryAtMs, setRetryAtMs] = useState(0);
    useEffect(() => {
        const timer = window.setInterval(() => {
            setNow(Date.now());
        }, 1000);
        return () => {
            window.clearInterval(timer);
        };
    }, []);
    const snapshot = state.snapshot;
    if (!snapshot?.outcome || !snapshot.statistics) return null;
    const { outcome, statistics } = snapshot;
    const won = outcome.winner === 'YOU';
    const remainingMs = Math.max(0, Date.parse(snapshot.expiresAt) - (now + state.clockOffsetMs));
    const waitSeconds = Math.max(0, Math.ceil((retryAtMs - now) / 1000));
    const disabled = leaving || state.action !== undefined || state.connection === 'offline' || streamReplaced;
    const leave = async () => {
        setLeaving(true);
        setFailure(undefined);
        try {
            await leaveGame(gameId);
            navigate('/');
        } catch (error) {
            if (error instanceof GatewayError) {
                setFailure(error.problem);
                setRetryAtMs(Date.now() + (error.problem.retryAfterSeconds ?? 0) * 1000);
            } else setFailure({ status: 0, code: 'transport-failure', messageKey: 'problem.serviceUnavailable' });
        } finally {
            setLeaving(false);
        }
    };
    return (
        <section className="results-screen" aria-labelledby="results-title">
            <div className={`result-summary${won ? '' : ' is-loss'}`}>
                <div>
                    <p className="eyebrow">{t('results.complete')}</p>
                    <h1 id="results-title">{t(won ? 'results.victory' : 'results.defeat')}</h1>
                    <p className="result-copy">
                        {t(outcome.reason === 'FLEET_DESTROYED' ? 'results.fleetDestroyed' : 'results.resignation')}
                    </p>
                </div>
                <div className="fleet-outcome">
                    <h2>{t('results.fleetCondition')}</h2>
                    {(['intact', 'damaged', 'sunk'] as const).map((condition) => (
                        <div role="group" aria-label={t(`results.${condition}`)} key={condition}>
                            <b>{statistics.you.fleet[condition]}</b>
                            <span>{t(`results.${condition}`)}</span>
                        </div>
                    ))}
                </div>
            </div>
            <div className="stats-grid" aria-label={t('results.statistics')}>
                <StatCard
                    title={t('results.matchTime')}
                    rows={[
                        { label: t('results.totalMatch'), value: duration(statistics.match.totalDurationMs) },
                        { label: t('results.placementPhase'), value: duration(statistics.match.placementDurationMs) },
                        { label: t('results.yourPlacement'), value: duration(statistics.you.placementDurationMs) },
                        {
                            label: t('results.opponentPlacement'),
                            value: duration(statistics.opponent.placementDurationMs),
                        },
                        { label: t('results.gameplay'), value: duration(statistics.match.gameplayDurationMs) },
                    ]}
                />
                <StatCard
                    title={t('results.turnsAccuracy')}
                    rows={[
                        { label: t('results.yourTurns'), value: String(statistics.you.turns.count) },
                        { label: t('results.opponentTurns'), value: String(statistics.opponent.turns.count) },
                        {
                            label: t('results.hitsShots'),
                            value: `${String(statistics.you.hits)} / ${String(statistics.you.shots)}`,
                        },
                        {
                            label: t('results.accuracy'),
                            value:
                                statistics.you.accuracy === undefined
                                    ? '—'
                                    : `${(statistics.you.accuracy * 100).toFixed(2)}%`,
                        },
                    ]}
                />
                <StatCard
                    title={t('results.turnTiming')}
                    rows={[
                        { label: t('results.yourClock'), value: duration(statistics.you.turns.totalMs) },
                        { label: t('results.opponentClock'), value: duration(statistics.opponent.turns.totalMs) },
                        { label: t('results.yourAverage'), value: duration(statistics.you.turns.averageMs) },
                        { label: t('results.opponentAverage'), value: duration(statistics.opponent.turns.averageMs) },
                        { label: t('results.fastest'), value: duration(statistics.you.turns.fastestMs) },
                        { label: t('results.slowest'), value: duration(statistics.you.turns.slowestMs) },
                    ]}
                />
            </div>
            <div className="results-boards">
                <div className="board-panel">
                    <h2>{t('results.enemyRevealed')}</h2>
                    <Board
                        board={snapshot.opponentBoard}
                        dimensions={ruleset.board}
                        label={t('results.enemyRevealed')}
                        disabled
                    />
                </div>
                <div className="board-panel">
                    <h2>{t('play.yourFleet')}</h2>
                    <Board board={snapshot.yourBoard} dimensions={ruleset.board} label={t('play.yourFleet')} disabled />
                </div>
            </div>
            <div className="results-footer">
                <p>
                    {t('results.availableFor')} <time dateTime={snapshot.expiresAt}>{duration(remainingMs)}</time>
                </p>
                <div className="results-actions">
                    <Button
                        disabled={disabled}
                        onClick={() => {
                            navigate('/create');
                        }}
                    >
                        {t('results.createAnother')}
                    </Button>
                    <Button
                        variant="primary"
                        disabled={disabled || !snapshot.allowedActions.includes('LEAVE') || waitSeconds > 0}
                        onClick={() => void leave()}
                    >
                        {t('results.leave')}
                    </Button>
                </div>
            </div>
            {failure && (
                <p role="alert">
                    {t(failure.messageKey)}{' '}
                    {waitSeconds > 0 && t('problem.retryAfter').replace('{seconds}', String(waitSeconds))}
                </p>
            )}
        </section>
    );
}
