import { useEffect, useRef, useState } from 'react';
import type { components } from '../contract/generated/api';
import { Board } from '../components/Board';
import { Announcer } from '../components/Announcer';
import { PlayerCard } from '../components/PlayerCard';
import { TurnPill } from '../components/TurnPill';
import { ConnectionStatus } from '../components/ConnectionStatus';
import type { Ruleset } from '../gateway/gameGateway';
import { getGameStore, type GameId } from '../session/gameStore';
import { useGame } from '../session/useGame';
import { useShotFeedback } from '../feedback/useShotFeedback';
import { useT } from '../i18n/useT';
import './Gameplay.css';

export function Gameplay({
    gameId,
    ruleset,
    streamReplaced,
    onCommand,
}: {
    gameId: GameId;
    ruleset: Ruleset;
    streamReplaced: boolean;
    onCommand: (request: components['schemas']['CommandRequest']) => Promise<void>;
}) {
    const t = useT();
    const state = useGame(gameId);
    const [now, setNow] = useState(Date.now);
    const [selectedBoard, setSelectedBoard] = useState<'target' | 'own'>('target');
    const tabs = useRef<Partial<Record<'target' | 'own', HTMLButtonElement>>>({});
    const unlockFeedback = useShotFeedback(state.snapshot?.lastShot);
    useEffect(() => {
        const timer = window.setInterval(() => {
            setNow(Date.now());
        }, 1000);
        return () => {
            window.clearInterval(timer);
        };
    }, []);
    const snapshot = state.snapshot;
    const store = getGameStore(gameId);
    if (!snapshot?.opponent) return null;
    const waitSeconds = Math.max(0, Math.ceil(((state.notice?.retryAtMs ?? 0) - now) / 1000));
    const disabled = state.action !== undefined || state.connection === 'offline' || streamReplaced || waitSeconds > 0;
    const fire = (target: components['schemas']['Coordinate']) => {
        if (
            disabled ||
            !snapshot.allowedActions.includes('FIRE') ||
            snapshot.opponentBoard.grid[target.rowIndex]?.[target.columnIndex] !== 'UNKNOWN'
        )
            return;
        unlockFeedback();
        const action = store.beginAction({ type: 'FIRE', target });
        if (action) void onCommand(action);
    };
    const pendingTarget = state.action?.command.type === 'FIRE' ? state.action.command.target : undefined;
    const remainingSeconds = Math.max(
        0,
        Math.ceil((Date.parse(snapshot.expiresAt) - (now + state.clockOffsetMs)) / 1000),
    );
    const countdown = `${String(Math.floor(remainingSeconds / 60))}:${String(remainingSeconds % 60).padStart(2, '0')}`;
    return (
        <section className="gameplay-screen" aria-label={t('game.playing')}>
            <h1 className="sr-only">{t('game.playing')}</h1>
            <div className="match-status">
                <PlayerCard player={snapshot.you} board={snapshot.yourBoard} fleet={ruleset.fleet} />
                <TurnPill turn={snapshot.turn} />
                <PlayerCard player={snapshot.opponent} board={snapshot.opponentBoard} fleet={ruleset.fleet} opponent />
            </div>
            <Announcer shot={snapshot.lastShot} />
            <div className="board-tabs" role="tablist" aria-label={t('play.chooseBoard')}>
                {(['target', 'own'] as const).map((board) => (
                    <button
                        key={board}
                        id={`tab-${board}`}
                        className="board-tab"
                        type="button"
                        role="tab"
                        ref={(element) => {
                            if (element) tabs.current[board] = element;
                        }}
                        aria-controls={`panel-${board}`}
                        onKeyDown={(event) => {
                            let next: 'target' | 'own';
                            if (event.key === 'Home') next = 'target';
                            else if (event.key === 'End') next = 'own';
                            else if (event.key === 'ArrowLeft' || event.key === 'ArrowRight')
                                next = board === 'target' ? 'own' : 'target';
                            else return;
                            event.preventDefault();
                            setSelectedBoard(next);
                            tabs.current[next]?.focus();
                        }}
                        aria-selected={selectedBoard === board}
                        tabIndex={selectedBoard === board ? 0 : -1}
                        onClick={() => {
                            setSelectedBoard(board);
                        }}
                    >
                        {t(board === 'target' ? 'play.enemyWaters' : 'play.yourFleet')}
                    </button>
                ))}
            </div>
            <div className="gameplay-boards" data-selected-board={selectedBoard}>
                <div
                    className="board-panel"
                    role="tabpanel"
                    aria-labelledby="tab-own"
                    id="panel-own"
                    data-board-role="own"
                >
                    <h2>{t('play.yourFleet')}</h2>
                    <Board
                        board={snapshot.yourBoard}
                        dimensions={ruleset.board}
                        label={t('play.yourFleet')}
                        shot={snapshot.lastShot?.by === 'OPPONENT' ? snapshot.lastShot : undefined}
                        disabled
                    />
                </div>
                <div
                    className="board-panel"
                    role="tabpanel"
                    aria-labelledby="tab-target"
                    id="panel-target"
                    data-board-role="target"
                >
                    <h2>{t('play.enemyWaters')}</h2>
                    <Board
                        board={snapshot.opponentBoard}
                        dimensions={ruleset.board}
                        label={t('play.enemyWaters')}
                        disabled={disabled || !snapshot.allowedActions.includes('FIRE')}
                        activeStates={['UNKNOWN']}
                        pendingTarget={pendingTarget}
                        shot={snapshot.lastShot?.by === 'YOU' ? snapshot.lastShot : undefined}
                        onActivate={fire}
                    />
                </div>
            </div>
            <footer className="gameplay-foot">
                <ConnectionStatus connection={state.connection} replaced={streamReplaced} />
                <span>
                    {t('play.expiresIn')} {countdown}
                </span>
            </footer>
        </section>
    );
}
