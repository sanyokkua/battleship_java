import { useCallback, useEffect, useMemo, useRef, useState, type Ref } from 'react';
import { Button } from '../components/Button';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { Dialog } from '../components/Dialog';
import { Meter } from '../components/Meter';
import { Pill } from '../components/Pill';
import { Qr } from '../components/Qr';
import { RulesetFacts } from '../components/RulesetFacts';
import { Stepper, type WaitingStep } from '../components/Stepper';
import { Toasts, type ToastMessage } from '../components/Toasts';
import { getGame, replaceInvitation, type Ruleset } from '../gateway/gameGateway';
import { useT } from '../i18n/useT';
import { type GameId } from '../session/gameStore';
import { useGame } from '../session/useGame';
import './WaitingRoom.css';

export function WaitingRoom({
    gameId,
    leaveSlotRef,
    ruleset,
    showCreatedToast = false,
    streamReplaced = false,
}: {
    gameId: GameId;
    leaveSlotRef: Ref<HTMLDivElement>;
    ruleset: Ruleset | undefined;
    showCreatedToast?: boolean;
    streamReplaced?: boolean;
}) {
    const t = useT();
    const state = useGame(gameId);
    const snapshot = state.snapshot;
    const [qrOpen, setQrOpen] = useState(false);
    const [confirmOpen, setConfirmOpen] = useState(false);
    const [pending, setPending] = useState(false);
    const [toast, setToast] = useState<ToastMessage | undefined>(() =>
        showCreatedToast ? { id: 1, kind: 'success', message: t('game.createdToast') } : undefined,
    );
    const [now, setNow] = useState(() => Date.now());
    const nextToastId = useRef(2);
    const lastRefreshExpiry = useRef<string | undefined>(undefined);
    const invitationUrl = snapshot?.invitationUrl;
    const safeInvitation = useMemo(() => invitationUrl?.replace(/(#invite=).*/, '$1••••••••'), [invitationUrl]);
    const invitationExpiresAt = snapshot?.invitationExpiresAt;
    const expiresAtMs = invitationExpiresAt ? Date.parse(invitationExpiresAt) : undefined;
    const remainingMs = expiresAtMs === undefined ? 0 : Math.max(0, expiresAtMs - (now + state.clockOffsetMs));
    const sessionExpiresAt = snapshot?.expiresAt;
    const sessionExpiresAtMs = sessionExpiresAt ? Date.parse(sessionExpiresAt) : undefined;
    const sessionRemainingMs =
        sessionExpiresAtMs === undefined ? 0 : Math.max(0, sessionExpiresAtMs - (now + state.clockOffsetMs));
    const totalMs = (state.meta?.limits.invitationLifetimeSeconds ?? 1) * 1000;
    const showToast = useCallback(
        (kind: ToastMessage['kind'], key: string) => {
            setToast({ id: nextToastId.current++, kind, message: t(key) });
        },
        [t],
    );
    const dismissToast = useCallback(() => {
        setToast(undefined);
    }, []);

    useEffect(() => {
        const timer = window.setInterval(() => {
            setNow(Date.now());
        }, 1000);
        return () => {
            window.clearInterval(timer);
        };
    }, []);
    useEffect(() => {
        if (
            expiresAtMs === undefined ||
            !snapshot ||
            !invitationExpiresAt ||
            state.connection !== 'connected' ||
            lastRefreshExpiry.current === invitationExpiresAt
        )
            return;
        const delay = Math.max(0, expiresAtMs - (Date.now() + state.clockOffsetMs)) + 250;
        const timer = window.setTimeout(() => {
            if (!navigator.onLine || state.connection !== 'connected') return;
            void getGame(gameId).then(
                () => {
                    lastRefreshExpiry.current = invitationExpiresAt;
                },
                () => {
                    if (navigator.onLine) lastRefreshExpiry.current = invitationExpiresAt;
                },
            );
        }, delay);
        return () => {
            window.clearTimeout(timer);
        };
    }, [expiresAtMs, gameId, invitationExpiresAt, snapshot, state.clockOffsetMs, state.connection]);

    const copyLink = async () => {
        if (!invitationUrl) return;
        try {
            await navigator.clipboard.writeText(invitationUrl);
            showToast('success', 'game.copySuccess');
        } catch {
            showToast('error', 'game.copyFailure');
        }
    };
    const doReplace = async () => {
        if (!navigator.onLine || state.connection !== 'connected') return;
        setConfirmOpen(false);
        setPending(true);
        try {
            await replaceInvitation(gameId);
        } catch {
            showToast('error', 'game.replaceFailure');
        } finally {
            setPending(false);
        }
    };
    const phase: WaitingStep =
        snapshot?.phase === 'WAITING'
            ? 'Waiting'
            : snapshot?.phase === 'PLACEMENT'
              ? 'Placement'
              : snapshot?.phase === 'PLAYING' || snapshot?.phase === 'FINISHED' || snapshot?.phase === 'ABANDONED'
                ? 'Battle'
                : 'Created';
    const allowed = snapshot?.allowedActions ?? [];
    return (
        <section className="waiting-screen">
            <div className="waiting-heading">
                <h1>{t(snapshot?.phase === 'PLACEMENT' ? 'game.placement' : 'game.waiting')}</h1>
                <div className="screen-heading-actions">
                    <ConnectionStatus connection={state.connection} replaced={streamReplaced} />
                    <div ref={leaveSlotRef} />
                </div>
            </div>
            <Stepper current={phase} />
            {ruleset && <RulesetFacts ruleset={ruleset} />}
            {snapshot?.phase === 'WAITING' && <p role="status">{t('game.waitingNotice')}</p>}
            {snapshot?.phase === 'WAITING' && sessionExpiresAt && (
                <p>
                    {t('game.sessionLifetime')}: {Math.ceil(sessionRemainingMs / 1000)} s{' '}
                    <time dateTime={sessionExpiresAt}>{new Date(sessionExpiresAt).toLocaleTimeString()}</time>
                </p>
            )}
            <div className="waiting-card">
                {!invitationUrl ? (
                    <Pill tone={snapshot?.phase === 'WAITING' ? 'warning' : 'good'}>
                        {snapshot?.phase === 'WAITING' ? t('game.invitationExpired') : t('game.placement')}
                    </Pill>
                ) : (
                    <>
                        <label className="waiting-link-label" htmlFor="invitation-link">
                            {t('game.share')}
                        </label>
                        <input id="invitation-link" className="waiting-link" value={safeInvitation ?? ''} readOnly />
                        <div className="waiting-actions">
                            <Button onClick={() => void copyLink()}>{t('game.copyLink')}</Button>
                            <Button
                                onClick={() => {
                                    setQrOpen(true);
                                }}
                            >
                                {t('game.showQr')}
                            </Button>
                        </div>
                        {expiresAtMs !== undefined && (
                            <>
                                <p>
                                    {t('game.invitationLifetime')}: {Math.ceil(remainingMs / 1000)} s
                                </p>
                                <Meter value={remainingMs / totalMs} label={t('game.invitationLifetime')} />
                            </>
                        )}
                    </>
                )}
                {allowed.includes('NEW_INVITATION') && (
                    <Button
                        disabled={pending || state.connection !== 'connected'}
                        onClick={() => {
                            setConfirmOpen(true);
                        }}
                    >
                        {t('game.replaceInvitation')}
                    </Button>
                )}
            </div>
            <Toasts toast={toast} onDismiss={dismissToast} />
            <Dialog
                open={qrOpen}
                titleId="invitation-qr-title"
                onClose={() => {
                    setQrOpen(false);
                }}
            >
                <div className="dialog-head">
                    <h2 id="invitation-qr-title">{t('game.qrTitle')}</h2>
                </div>
                <div className="dialog-body waiting-qr-body">
                    {invitationUrl && <Qr value={invitationUrl} label={t('game.qrLabel')} />}
                    <p>{t('game.qrPrivacy')}</p>
                </div>
                <div className="dialog-actions">
                    <Button
                        onClick={() => {
                            setQrOpen(false);
                        }}
                    >
                        {t('common.cancel')}
                    </Button>
                </div>
            </Dialog>
            <ConfirmDialog
                open={confirmOpen}
                title={t('game.replaceTitle')}
                message={t('game.replaceWarning')}
                confirmLabel={t('game.confirmReplace')}
                confirmDisabled={pending || state.connection !== 'connected'}
                onConfirm={() => {
                    void doReplace();
                }}
                onClose={() => {
                    setConfirmOpen(false);
                }}
            />
        </section>
    );
}
