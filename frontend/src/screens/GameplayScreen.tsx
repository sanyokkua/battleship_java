import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate} from 'react-router-dom';
import {useSessionGuard} from '../hooks/useSessionGuard';
import {useGameplay} from '../hooks/useGameplay';
import {saveStage} from '../services/GameBrowserStorage';
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
import './GameplayScreen.css';

// Every game edition has exactly 10 ships — see MOCKUP/spec §8.3 ("edition total": 10 ships
// either way, Ukrainian sizes 1-4 / Milton Bradley sizes 2-5). No need to derive this from
// the API (which doesn't expose it once a session exists anyway).
const TOTAL_SHIPS = 10;

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
    const {state, shoot, loading, error} = useGameplay(sessionId ?? '', player?.playerId ?? '');

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
    }

    useEffect(() => {
        if (state?.hasWinner) {
            saveStage('FINISHED');
            navigate('/game/results', {replace: true});
        }
    }, [state?.hasWinner, navigate]);

    // Tracks the previous isPlayerActive value purely to detect an actual flip — starts
    // `undefined` so the *first* time state loads (regardless of whether the player
    // happens to start active or waiting) is never mistaken for a flip and never forces
    // the tab away from its default ('target'). Only forces a switch AT THE MOMENT the
    // value changes, never on a same-value re-render (e.g. a same-value poll refetch
    // every 5s) — so a manual tab switch made mid-turn is never fought.
    const prevIsPlayerActiveRef = useRef<boolean | undefined>(undefined);

    useEffect(() => {
        if (!state) return;
        const current = state.isPlayerActive;
        const prev = prevIsPlayerActiveRef.current;
        if (prev !== undefined && prev !== current) {
            setActiveTab(current ? 'target' : 'fleet');
        }
        prevIsPlayerActiveRef.current = current;
        // Deliberately keyed on state?.isPlayerActive only — see comment above; re-running this
        // effect on every `state` reference change (e.g. same-value poll refetch) would defeat
        // the "only fire on an actual flip" guard this effect exists to provide.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [state?.isPlayerActive]);

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
                    onChange={setActiveTab}
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
                        <Board mode="own" field={state.playerField} readonly/>
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
            </div>
        </div>
    );
}
