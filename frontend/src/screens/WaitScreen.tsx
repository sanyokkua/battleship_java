import {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate} from 'react-router-dom';
import copy from 'copy-to-clipboard';
import {APP_BASE_PATH} from '../config/appConfig';
import {buildCanonicalJoinUrl} from '../config/appBasePath';
import {useSessionGuard} from '../hooks/useSessionGuard';
import {useWaitRoom} from '../hooks/useWaitRoom';
import {saveStage} from '../services/GameBrowserStorage';
import {StepTracker} from '../design/components/StepTracker/StepTracker';
import {Button} from '../design/components/Button/Button';
import {LoadingView} from '../widgets/layout/LoadingView';
import {useNotify} from '../widgets/feedback/useNotify';
import {QrCodeSheet} from '../widgets/sharing/QrCodeSheet';
import './WaitScreen.css';

// Stages that mean the wait is still ongoing — anything else (PREPARATION, IN_GAME, FINISHED)
// means the wait is over and we should move on.
const STILL_WAITING_STAGES = new Set(['INITIALIZED', 'WAITING_FOR_PLAYERS']);

/**
 * Post-join waiting room ("/game/wait") — shown while `GameStage` is
 * `INITIALIZED` or `WAITING_FOR_PLAYERS`. Polls session status via
 * `useWaitRoom` and, once the stage advances past waiting, persists the new
 * stage and navigates to `PreparationScreen` ("/game/preparation"). Also lets
 * the player copy the session id or a shareable join link to the clipboard.
 */
export function WaitScreen() {
    const {t} = useTranslation('screens');
    const navigate = useNavigate();
    const notify = useNotify();
    const [qrOpen, setQrOpen] = useState(false);

    const {sessionId, player} = useSessionGuard();
    const {stage, loading, refresh} = useWaitRoom(sessionId ?? '', player?.playerId ?? '');

    useEffect(() => {
        if (stage && !STILL_WAITING_STAGES.has(stage)) {
            saveStage(stage);
            navigate('/game/preparation', {replace: true});
        }
    }, [stage, navigate]);

    if (!sessionId || !player) {
        // Defensive only — StageGuard at the routing layer is responsible for redirecting
        // away from this screen when session/player data isn't present.
        return null;
    }

    if (loading) {
        return <LoadingView title={t('screens:loading.title')} subtitle={t('screens:loading.subtitle')}/>;
    }

    const canonicalJoinUrl = buildCanonicalJoinUrl(window.location.origin, APP_BASE_PATH, sessionId);
    const qrAvailable = stage === 'WAITING_FOR_PLAYERS' && canonicalJoinUrl !== null;

    const steps = [
        {key: 'create', label: t('screens:steps.create')},
        {key: 'wait', label: t('screens:steps.wait')},
        {key: 'prepare', label: t('screens:steps.prepare')},
        {key: 'battle', label: t('screens:steps.battle')},
    ];

    async function handleCopy() {
        if (!sessionId) return;
        // copy-to-clipboard falls back to a hidden-element + document.execCommand('copy')
        // when the async navigator.clipboard API is unavailable (e.g. plain-HTTP LAN
        // addresses, which aren't a "secure context"), and never throws.
        const ok = await copy(sessionId);
        if (ok) {
            notify.success('id.copied');
        }
    }

    async function handleCopyLink() {
        if (!canonicalJoinUrl) return;
        const ok = await copy(canonicalJoinUrl);
        if (ok) {
            notify.success('link.copied');
        }
    }

    return (
        <div className="screen">
            <div className="wait-screen">
                <StepTracker steps={steps} currentIndex={1}/>
                <div className="hero-illust" aria-hidden="true">
                    🛰️
                </div>
                <h2 className="title">{t('screens:wait.hello', {name: player.playerName})}</h2>
                <p className="sub">{t('screens:wait.subtitle')}</p>
                <div className="card">
                    <label className="fld">{t('screens:join.idLabel')}</label>
                    <div className="share-box">
                        <code>{sessionId}</code>
                        <Button variant="primary" size="sm" onClick={handleCopy}>
                            {t('screens:wait.copy')}
                        </Button>
                        <div className="share-actions">
                            <Button variant="ghost" size="sm" onClick={handleCopyLink} disabled={!canonicalJoinUrl}>
                                {t('screens:wait.copyLink')}
                            </Button>
                            {qrAvailable && (
                                <Button variant="ghost" size="sm" onClick={() => setQrOpen(true)}>
                                    {t('screens:wait.showQr')}
                                </Button>
                            )}
                        </div>
                    </div>
                    <div className="waiting-line">
                        {t('screens:wait.waiting')}
                        <span className="waiting-dots" aria-hidden="true">
              <i></i>
              <i></i>
              <i></i>
            </span>
                    </div>
                    <Button variant="ghost" size="sm" onClick={() => void refresh()}>
                        ⟳ {t('common:button.refresh')}
                    </Button>
                </div>
            </div>
            {canonicalJoinUrl !== null && (
                <QrCodeSheet open={qrOpen} url={canonicalJoinUrl} onClose={() => setQrOpen(false)}/>
            )}
        </div>
    );
}
