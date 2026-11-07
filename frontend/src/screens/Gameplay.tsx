import { useCallback, useEffect, useRef, useState } from 'react';
import type { components } from '../contract/generated/api';
import { Board, BoardLegend } from '../components/Board';
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
}: Readonly<{
    gameId: GameId;
    ruleset: Ruleset;
    streamReplaced: boolean;
    onCommand: (request: components['schemas']['CommandRequest']) => Promise<void>;
}>) {
    const t = useT();
    const state = useGame(gameId);
    const [now, setNow] = useState(Date.now);
    const [selectedBoard, setSelectedBoard] = useState<'target' | 'own'>('target');
    const [highlighted, setHighlighted] = useState<components['schemas']['Shot']>();
    const tabs = useRef<Partial<Record<'target' | 'own', HTMLButtonElement>>>({});
    const tabList = useRef<HTMLDivElement>(null);
    const panels = useRef<Partial<Record<'target' | 'own', HTMLDivElement>>>({});
    const timer = useRef<ReturnType<typeof globalThis.setTimeout> | undefined>(undefined);
    const selected = useRef(selectedBoard);
    const observed = useRef<
        { key: string; turn: components['schemas']['Side'] | undefined; connected: boolean } | undefined
    >(undefined);
    const wasTabbed = useRef<boolean | undefined>(undefined);
    const lastManualSelection = useRef(0);
    const lastBothBoardLayout = useRef(0);
    const unlockFeedback = useShotFeedback(state.snapshot?.lastShot);
    const selectBoard = useCallback((board: 'target' | 'own') => {
        if (selected.current !== board && panels.current[selected.current]?.contains(document.activeElement)) {
            tabs.current[board]?.focus();
        }
        selected.current = board;
        setSelectedBoard(board);
    }, []);
    const cancelSwitch = () => {
        globalThis.clearTimeout(timer.current);
        timer.current = undefined;
    };
    const snapshot = state.snapshot;
    const shot = snapshot?.lastShot;
    const shotKey = shot ? `${shot.by}:${String(shot.target.rowIndex)}:${String(shot.target.columnIndex)}` : '';
    const turn = snapshot?.turn;
    const connected = state.connection === 'connected' && !streamReplaced;
    useEffect(() => {
        if (!snapshot || !turn) return;
        const previous = observed.current;
        observed.current = { key: shotKey, turn, connected };
        const tabbed = tabList.current && getComputedStyle(tabList.current).display !== 'none';
        if (!connected || !previous?.connected) {
            globalThis.clearTimeout(timer.current);
            timer.current = undefined;
            setHighlighted(undefined);
            if (connected && tabbed) selectBoard(turn === 'YOU' ? 'target' : 'own');
            return;
        }
        if (!shot || shotKey === previous.key) return;
        globalThis.clearTimeout(timer.current);
        timer.current = undefined;
        setHighlighted(shot);
        if (!tabbed) return;
        selectBoard(shot.by === 'YOU' ? 'target' : 'own');
        if (turn !== shot.by) {
            timer.current = globalThis.setTimeout(() => {
                timer.current = undefined;
                if (
                    observed.current?.key !== shotKey ||
                    observed.current.turn !== turn ||
                    !observed.current.connected ||
                    !tabList.current ||
                    getComputedStyle(tabList.current).display === 'none'
                )
                    return;
                selectBoard(turn === 'YOU' ? 'target' : 'own');
            }, 1000);
        }
    }, [snapshot, shot, shotKey, turn, connected, selectBoard]);
    useEffect(
        () => () => {
            globalThis.clearTimeout(timer.current);
        },
        [],
    );
    useEffect(() => {
        const tabsElement = tabList.current;
        const frame = tabsElement?.closest('.app-frame');
        if (!tabsElement) return;
        const onLayout = () => {
            const tabbed = getComputedStyle(tabsElement).display !== 'none';
            if (
                wasTabbed.current === false &&
                tabbed &&
                lastManualSelection.current < lastBothBoardLayout.current &&
                observed.current?.connected &&
                observed.current.turn
            ) {
                selectBoard(observed.current.turn === 'YOU' ? 'target' : 'own');
            }
            if (!tabbed && wasTabbed.current !== false) {
                lastBothBoardLayout.current = performance.now();
                globalThis.clearTimeout(timer.current);
            }
            wasTabbed.current = tabbed;
        };
        onLayout();
        globalThis.addEventListener('resize', onLayout);
        const observer = typeof ResizeObserver === 'undefined' ? undefined : new ResizeObserver(onLayout);
        if (frame) observer?.observe(frame);
        return () => {
            globalThis.removeEventListener('resize', onLayout);
            observer?.disconnect();
        };
    }, [selectBoard]);
    useEffect(() => {
        const timer = globalThis.setInterval(() => {
            setNow(Date.now());
        }, 1000);
        return () => {
            globalThis.clearInterval(timer);
        };
    }, []);
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
            <div className="board-tabs" role="tablist" aria-label={t('play.chooseBoard')} ref={tabList}>
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
                            lastManualSelection.current = event.timeStamp;
                            cancelSwitch();
                            selectBoard(next);
                            tabs.current[next]?.focus();
                        }}
                        aria-selected={selectedBoard === board}
                        tabIndex={selectedBoard === board ? 0 : -1}
                        onClick={(event) => {
                            lastManualSelection.current = event.timeStamp;
                            cancelSwitch();
                            selectBoard(board);
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
                    ref={(element) => {
                        if (element) panels.current.own = element;
                    }}
                >
                    <h2>{t('play.yourFleet')}</h2>
                    <Board
                        board={snapshot.yourBoard}
                        dimensions={ruleset.board}
                        label={t('play.yourFleet')}
                        shot={snapshot.lastShot?.by === 'OPPONENT' ? snapshot.lastShot : undefined}
                        highlightedShot={highlighted?.by === 'OPPONENT' ? highlighted.target : undefined}
                        disabled
                    />
                    <BoardLegend own />
                </div>
                <div
                    className="board-panel"
                    role="tabpanel"
                    aria-labelledby="tab-target"
                    id="panel-target"
                    data-board-role="target"
                    ref={(element) => {
                        if (element) panels.current.target = element;
                    }}
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
                        highlightedShot={highlighted?.by === 'YOU' ? highlighted.target : undefined}
                        onActivate={fire}
                    />
                    <BoardLegend />
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
