import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate} from 'react-router-dom';
import {useSessionGuard} from '../hooks/useSessionGuard';
import {useGameplay} from '../hooks/useGameplay';
import {saveStage} from '../services/GameBrowserStorage';
import type {ResponseGameplayStateDto} from '../logic/ApplicationTypes';
import {classifyGameplayFeedback} from '../logic/gameplayFeedback';
import {formatCoordinateLabel} from '../logic/boardCoordinates';
import {Button} from '../design/components/Button/Button';
import {LoadingView} from '../widgets/layout/LoadingView';
import {Board} from '../widgets/board/Board';
import {BoardTabs} from '../widgets/board/BoardTabs';
import {Legend} from '../widgets/board/Legend';
import {PlayerCard} from '../widgets/gameplay/PlayerCard';
import {TurnBanner} from '../widgets/gameplay/TurnBanner';
import {useNotify} from '../widgets/feedback/useNotify';
import {useToastContext} from '../widgets/feedback/ToastContext';
import {resolveErrorMessageKey} from '../widgets/feedback/errorMapping';
import {GameAdapterError} from '../adapters/AdapterErrors';
import {useAudioFeedback} from '../audio/AudioFeedbackContext';
import './GameplayScreen.css';

// Every game edition has exactly 10 ships (Ukrainian sizes 1-4 / Milton Bradley
// sizes 2-5). No need to derive this from the API (which doesn't expose it once
// a session exists anyway).
const TOTAL_SHIPS = 10;

// How long a just-shot cell's flash animation runs (must match Board.css's
// board-cell-shot-flash keyframes duration).
const HIGHLIGHT_DURATION_MS = 1100;
// How long the mobile view lingers on the fleet board after an opponent's shot
// resolves as a miss before auto-switching to the target board — comfortably
// longer than HIGHLIGHT_DURATION_MS so the flash always finishes before the switch.
const SWITCH_DELAY_MS = 1500;

type MaxCells = { player: number | null; opponent: number | null };

/**
 * Active-battle screen ("/game/gameplay") — shown during the `IN_GAME` stage.
 * Polls live game state and issues shots via `useGameplay`, renders the
 * target/fleet boards with `Board`/`BoardTabs`, and auto-switches the active
 * tab when the turn flips. Once `state.hasWinner` is observed it persists the
 * `FINISHED` stage and navigates to `ResultsScreen` ("/game/results").
 */
export function GameplayScreen() {
    const {t} = useTranslation(['screens', 'notifications', 'errors']);
    const navigate = useNavigate();
    const notify = useNotify();
    const {push} = useToastContext();

    const {sessionId, player} = useSessionGuard();
    const {state, shoot, loading, error, refresh} = useGameplay(sessionId ?? '', player?.playerId ?? '');
    const audio = useAudioFeedback();

    // 'target' here is only a pre-any-state placeholder — it's immediately overwritten by
    // the correct tab (based on state.isPlayerActive) the moment `state` first arrives, in
    // the maxCells render-time block below.
    const [activeTab, setActiveTab] = useState<'target' | 'fleet'>('target');

    // Known API limitation: `*NumberOfAliveCells` are absolute counts and the true edition
    // maximum (20 for Ukrainian, 30 for Milton Bradley) isn't exposed by any endpoint once a
    // session exists. We capture the first observed value of each count as that player's 100%
    // baseline — accurate as long as this screen mounts at the true start of IN_GAME (before
    // any shots land). If this screen is first mounted mid-game (e.g. a page refresh after some
    // shots already landed), the captured "max" will be an underestimate and the bar will start
    // below 100% even though it's showing correct relative progress from that point forward.
    //
    // Captured via useState (set once, guarded by a null check) rather than a plain useRef:
    // reading/writing a ref's `.current` during render is disallowed (react-hooks/refs) since it
    // can desync the component from React's rendered output. Setting state during render is the
    // sanctioned "derive once from props/state on first render" escape hatch (see the React docs
    // on adjusting state during render) and still only runs this branch once, on the render where
    // `state` first arrives — every render after that, both fields are already non-null and the
    // condition is false, so this never loops.
    const [maxCells, setMaxCells] = useState<MaxCells>({player: null, opponent: null});
    if (state && (maxCells.player === null || maxCells.opponent === null)) {
        setMaxCells({
            player: maxCells.player ?? state.playerNumberOfAliveCells,
            opponent: maxCells.opponent ?? state.opponentNumberOfAliveCells,
        });
        // Same "first arrival of state" guard as maxCells above: the initial tab must reflect
        // whose turn it actually is on this first snapshot (waiting players have nothing to
        // target yet), not an unconditional 'target' default. Set here, during render, rather
        // than in the [state] effect below, so there's no one-frame flash of the wrong board
        // before the effect would otherwise correct it.
        setActiveTab(state.isPlayerActive ? 'target' : 'fleet');
    }

    useEffect(() => {
        if (state?.hasWinner) {
            saveStage('FINISHED');
            navigate('/game/results', {replace: true});
        }
    }, [state?.hasWinner, navigate]);

    // Tracks the previous isPlayerActive value purely to detect an actual flip — starts
    // `undefined` so the *first* time state loads (regardless of whether the player
    // happens to start active or waiting) is never mistaken for a flip. The initial tab
    // itself is already set correctly for this first snapshot by the maxCells render-time
    // block above, so this ref's job is narrower: only force a switch AT THE MOMENT the
    // value changes on a later render, never on a same-value re-render (e.g. a same-value
    // poll refetch every 5s) — so a manual tab switch made mid-turn is never fought.
    const prevIsPlayerActiveRef = useRef<boolean | undefined>(undefined);

    // Full gameplay snapshots are the source of truth for both incoming and outgoing result
    // feedback. `null` specifically marks "no snapshot yet", so a refresh or remount mid-game
    // seeds the baseline without replaying shots that happened before this screen observed it.
    const prevStateRef = useRef<ResponseGameplayStateDto | null>(null);

    // Which own-board cells are currently mid-flash (see Board.css's .is-shot-flash), keyed
    // `${row}-${col}` to match the ghostCells convention. A per-cell timer (not one shared
    // timer) matters because a hit/sunk shot grants the opponent another turn, so multiple
    // shots can land on consecutive pushes before the turn flips back — each cell's flash
    // must run its own full duration independently rather than one shot resetting another's.
    const [highlightedCells, setHighlightedCells] = useState<Set<string>>(new Set());
    const highlightTimersRef = useRef(new Map<string, ReturnType<typeof setTimeout>>());

    // A single pending mobile tab-switch timer — a new delayed switch always supersedes an
    // old one, and a manual tab tap (see handleTabChange) always cancels it outright.
    const switchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    function scheduleDelayedSwitch(tab: 'target' | 'fleet') {
        if (switchTimerRef.current) {
            clearTimeout(switchTimerRef.current);
        }
        switchTimerRef.current = setTimeout(() => {
            switchTimerRef.current = null;
            setActiveTab(tab);
        }, SWITCH_DELAY_MS);
    }

    function cancelPendingSwitch() {
        if (switchTimerRef.current) {
            clearTimeout(switchTimerRef.current);
            switchTimerRef.current = null;
        }
    }

    function handleTabChange(tab: 'target' | 'fleet') {
        cancelPendingSwitch();
        setActiveTab(tab);
    }

    // The delayed-switch decision and all visual/toast/audio result feedback share one full
    // snapshot diff. This keeps turn changes, highlights, toasts, and playback aligned for a
    // pushed update or a refetched state without adding a direct audio path to handleShot.
    useEffect(() => {
        if (!state) return;
        const previousState = prevStateRef.current;
        const current = state.isPlayerActive;
        const prev = prevIsPlayerActiveRef.current;

        if (previousState === null) {
            prevStateRef.current = state;
            prevIsPlayerActiveRef.current = current;
            return;
        }

        const feedbackEvents = classifyGameplayFeedback(previousState, state);
        feedbackEvents.forEach(event => audio.play(event.outcome));
        const newlyShotKeys = new Set<string>();
        feedbackEvents
            .filter(event => event.board === 'player')
            .forEach(event => {
                const [row, col] = event.cellKey.split(',').map(Number);
                newlyShotKeys.add(`${row}-${col}`);
                const coordinate = formatCoordinateLabel(row, col);
                if (event.outcome === 'MISS') {
                    notify.info('incomingShot.miss', {coordinate});
                } else if (event.outcome === 'DESTROYED') {
                    notify.warn('incomingShot.sunk', {coordinate});
                } else {
                    notify.warn('incomingShot.hit', {coordinate});
                }
            });

        if (newlyShotKeys.size > 0) {
            // highlightTimersRef is the source of truth for "which cells are currently
            // flashing" — its key set is snapshotted into `highlightedCells` state below
            // every time it changes, so BoardCell renders read a plain value rather than
            // reconstructing it from a setState updater callback.
            newlyShotKeys.forEach(key => {
                const existingTimer = highlightTimersRef.current.get(key);
                if (existingTimer) {
                    clearTimeout(existingTimer);
                }
                const timer = setTimeout(() => {
                    highlightTimersRef.current.delete(key);
                    setHighlightedCells(new Set(highlightTimersRef.current.keys()));
                }, HIGHLIGHT_DURATION_MS);
                highlightTimersRef.current.set(key, timer);
            });
            setHighlightedCells(new Set(highlightTimersRef.current.keys()));
        }

        if (prev !== undefined && prev !== current) {
            if (current) {
                // false -> true: the opponent's shot just resolved as a miss and the turn
                // passed back. Give the player a moment to see the flash before switching —
                // unless, for some reason, no diff was detected this pass, in which case
                // switch immediately so the UI never gets stuck on the fleet board.
                if (newlyShotKeys.size > 0) {
                    scheduleDelayedSwitch('target');
                } else {
                    cancelPendingSwitch();
                    setActiveTab('target');
                }
            } else {
                // true -> false: the player just fired — no highlight to protect on this side.
                cancelPendingSwitch();
                setActiveTab('fleet');
            }
        }

        prevStateRef.current = state;
        prevIsPlayerActiveRef.current = current;
        // Deliberately excludes `notify` — useNotify() returns a fresh object every render,
        // and keying on it would re-run this diff on every render instead of only when
        // `state` changes; the diff itself is a no-op once `prevPlayerFieldRef` catches up
        // to the current `state`, so this stays correct either way, just noisier.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [audio, state]);

    useEffect(() => {
        const highlightTimers = highlightTimersRef.current;
        return () => {
            highlightTimers.forEach(timer => clearTimeout(timer));
            highlightTimers.clear();
            cancelPendingSwitch();
        };
    }, []);

    if (!sessionId || !player) {
        // Defensive only — StageGuard at the routing layer is responsible for redirecting
        // away from this screen when session/player data isn't present.
        return null;
    }

    if (loading && !state) {
        return <LoadingView title={t('screens:loading.title')} subtitle={t('screens:loading.subtitle')}/>;
    }

    if (!state) {
        return null;
    }

    const playerMax = maxCells.player ?? state.playerNumberOfAliveCells;
    const opponentMax = maxCells.opponent ?? state.opponentNumberOfAliveCells;
    const playerCellsPercent = playerMax > 0 ? (state.playerNumberOfAliveCells / playerMax) * 100 : 0;
    const opponentCellsPercent = opponentMax > 0 ? (state.opponentNumberOfAliveCells / opponentMax) * 100 : 0;

    const turnBannerText = state.isPlayerActive
        ? t('screens:gameplay.yourTurn')
        : t('screens:gameplay.theirTurn', {name: state.opponentName});

    async function handleShot(row: number, column: number) {
        if (!state) return;
        if (!state.isPlayerActive) {
            notify.info('turn.notYours');
            return;
        }
        if (state.opponentField[row][column].hasShot) {
            notify.info('shot.alreadyShot');
            return;
        }

        const result = await shoot({row, column});
        if (result === 'HIT') {
            notify.warn('shot.hit');
        } else if (result === 'DESTROYED') {
            notify.warn('shot.sunk');
        } else if (result === 'MISS') {
            notify.info('shot.miss');
        } else {
            // null = adapter/backend error; useGameplay's `error` state holds the GameAdapterError
            // that caused it (set synchronously by shoot() before returning null above).
            const adapterError = error ?? new GameAdapterError('Shoot failed');
            const errorKey = resolveErrorMessageKey(adapterError);
            push({
                variant: 'err',
                title: t('notifications:error.generic.title'),
                message: t(`errors:${errorKey}`),
            });
        }
    }

    return (
        <div className="screen">
            <div className="gameplay-screen">
                <div className="scoreboard">
                    <PlayerCard
                        variant="you"
                        name={state.playerName}
                        youLabel={t('screens:gameplay.you')}
                        cellsLabel={t('screens:gameplay.cells')}
                        cellsValue={state.playerNumberOfAliveCells}
                        cellsPercent={playerCellsPercent}
                        shipsLabel={t('screens:gameplay.ships')}
                        shipsAliveCount={state.playerNumberOfAliveShips}
                        shipsTotal={TOTAL_SHIPS}
                    />
                    <PlayerCard
                        variant="foe"
                        name={state.opponentName}
                        isActiveTurn={!state.isPlayerActive}
                        cellsLabel={t('screens:gameplay.cells')}
                        cellsValue={state.opponentNumberOfAliveCells}
                        cellsPercent={opponentCellsPercent}
                        shipsLabel={t('screens:gameplay.ships')}
                        shipsAliveCount={state.opponentNumberOfAliveShips}
                        shipsTotal={TOTAL_SHIPS}
                    />
                </div>

                <TurnBanner isYourTurn={state.isPlayerActive} text={turnBannerText}/>

                <BoardTabs
                    active={activeTab}
                    onChange={handleTabChange}
                    targetLabel={t('screens:gameplay.tabTarget')}
                    fleetLabel={t('screens:gameplay.tabFleet')}
                />

                <div className="boards-area">
                    <div className={`board-panel bp-target${activeTab !== 'target' ? ' hide' : ''}`}>
                        <div className="board-head">
                            <h3>{t('screens:gameplay.targetBoard', {name: state.opponentName})}</h3>
                            <span className="tag">{t('screens:gameplay.fireHint')}</span>
                        </div>
                        <Board mode="target" field={state.opponentField} onCellClick={handleShot}/>
                    </div>
                    <div className={`board-panel bp-fleet${activeTab !== 'fleet' ? ' hide' : ''}`}>
                        <div className="board-head">
                            <h3>{t('screens:gameplay.fleetLabel')}</h3>
                            <span className="tag">{t('screens:gameplay.defensiveView')}</span>
                        </div>
                        <Board mode="own" field={state.playerField} readonly highlightedCells={highlightedCells}/>
                    </div>
                </div>

                <Legend
                    withNoGo={false}
                    labels={{
                        water: t('screens:legend.water'),
                        ship: t('screens:legend.ship'),
                        hit: t('screens:legend.hit'),
                        miss: t('screens:legend.miss'),
                        sunk: t('screens:legend.sunk'),
                    }}
                />

                <div className="gameplay-utilities">
                    <Button
                        variant="ghost"
                        size="sm"
                        type="button"
                        aria-pressed={audio.isEnabled()}
                        aria-label={t('screens:gameplay.soundLabel', {
                            state: audio.isEnabled()
                                ? t('screens:gameplay.soundOn')
                                : t('screens:gameplay.soundOff'),
                        })}
                        onClick={() => audio.setEnabled(!audio.isEnabled())}
                    >
                        {t('screens:gameplay.soundLabel', {
                            state: audio.isEnabled()
                                ? t('screens:gameplay.soundOn')
                                : t('screens:gameplay.soundOff'),
                        })}
                    </Button>
                    <Button variant="ghost" size="sm" type="button" onClick={() => void refresh()}>
                        ⟳ {t('common:button.refresh')}
                    </Button>
                </div>
            </div>
        </div>
    );
}
