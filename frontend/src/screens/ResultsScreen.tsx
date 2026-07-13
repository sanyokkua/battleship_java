import {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate} from 'react-router-dom';
import {useSessionGuard} from '../hooks/useSessionGuard';
import {useGameAdapter} from '../adapters/GameAdapterContext';
import {clearGameData} from '../services/GameBrowserStorage';
import {Board} from '../widgets/board/Board';
import {Legend} from '../widgets/board/Legend';
import {Button} from '../design/components/Button/Button';
import {LoadingView} from '../widgets/layout/LoadingView';
import type {ResponseGameplayStateDto} from '../logic/ApplicationTypes';
import './ResultsScreen.css';

// Total ships per edition is fixed at 10 (Ukrainian and Milton Bradley both use a
// 10-ship fleet, just with different size makeups) — see EDITION_SHIP_SIZES in
// MockGameAdapter.ts / the backend's GameEditionConfiguration classes. Ships you
// personally sank = that fixed total minus how many of the opponent's ships are
// still alive at game end.
const TOTAL_SHIPS_PER_EDITION = 10;

/**
 * Results screen — ported from MOCKUP.html's `results:()=>...` screen-render
 * function (`.result-hero`, `.stat-strip`, `.boards-area`/`.board-panel`).
 *
 * SPEC §8.3 screen 8: win/lose hero from isPlayerWinner/winnerPlayerName, both
 * boards read-only, "Return to main menu". SPEC §8.5 explicitly excludes the
 * mockup's "Hits" and "Time" stats (not API-backed) — only "Ships sunk" is shown.
 *
 * Content gap: screens.json's `results` group has a `winSubtitle` key
 * ("{{name}} sank the entire enemy fleet.") but no distinct lose-subtitle key —
 * the mockup itself only demos the win variant. Rather than invent a new i18n
 * key (out of scope for this ticket, which may only create ResultsScreen.tsx),
 * we reuse `winSubtitle` with the actual winner's name for both outcomes: the
 * sentence is winner-framed and reads correctly whether you're the winner
 * ("I sank the entire enemy fleet") or the loser ("they sank the entire enemy
 * fleet") — it never claims *you* did anything you didn't.
 */
export function ResultsScreen() {
    const {t} = useTranslation('screens');
    const navigate = useNavigate();
    const adapter = useGameAdapter();
    const {sessionId, player} = useSessionGuard();

    const [state, setState] = useState<ResponseGameplayStateDto | null>(null);
    const [error, setError] = useState(false);

    useEffect(() => {
        if (!sessionId || !player) return;
        adapter
            .getGameState(sessionId, player.playerId)
            .then(setState)
            .catch(() => setError(true));
        // Fetch once — the game is over, no polling needed.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    if (!sessionId || !player) {
        // Defensive only — StageGuard at the routing layer is responsible for redirecting
        // away from this screen when session/player data isn't present.
        return null;
    }

    if (error) {
        return <LoadingView title={t('screens:loading.title')} subtitle={t('screens:loading.subtitle')}/>;
    }

    if (!state) {
        return <LoadingView title={t('screens:loading.title')} subtitle={t('screens:loading.subtitle')}/>;
    }

    function handleReturnToMenu() {
        clearGameData();
        navigate('/', {replace: true});
    }

    const win = state.isPlayerWinner;
    const shipsSunk = TOTAL_SHIPS_PER_EDITION - state.opponentNumberOfAliveShips;

    const legendLabels = {
        water: t('screens:legend.water'),
        ship: t('screens:legend.ship'),
        hit: t('screens:legend.hit'),
        miss: t('screens:legend.miss'),
        sunk: t('screens:legend.sunk'),
    };

    return (
        <div className="screen">
            <div className={`result-hero ${win ? 'win' : 'lose'}`}>
                <div className="medal" aria-hidden="true">
                    {win ? '🏆' : '💥'}
                </div>
                <h2 className="title">{win ? t('screens:results.win') : t('screens:results.lose')}</h2>
                <p className="sub">{t('screens:results.winSubtitle', {name: state.winnerPlayerName})}</p>
                <div className="stat-strip">
                    <div className="b">
                        <div className="n">{shipsSunk}</div>
                        <div className="l">{t('screens:results.shipsSunk')}</div>
                    </div>
                </div>
            </div>

            <div className="boards-area">
                <div className="board-panel">
                    <div className="board-head">
                        <h3>{t('screens:gameplay.targetBoard', {name: state.opponentName})}</h3>
                    </div>
                    <Board mode="result-target" field={state.opponentField} readonly/>
                </div>
                <div className="board-panel">
                    <div className="board-head">
                        <h3>{t('screens:gameplay.fleetLabel')}</h3>
                    </div>
                    <Board mode="result-own" field={state.playerField} readonly/>
                </div>
            </div>

            <Legend withNoGo={false} labels={legendLabels}/>

            <Button variant="primary" className="results-menu-btn" onClick={handleReturnToMenu}>
                {t('screens:results.menuButton')}
            </Button>
        </div>
    );
}
