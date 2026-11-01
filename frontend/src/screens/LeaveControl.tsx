import { useState } from 'react';
import { createPortal } from 'react-dom';
import { navigate } from '../app/router';
import { Button } from '../components/Button';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { GatewayError, leaveGame } from '../gateway/gameGateway';
import { useT } from '../i18n/useT';
import type { GameId } from '../session/gameStore';
import { useGame } from '../session/useGame';

export function LeaveControl({ gameId, target }: Readonly<{ gameId: GameId; target: HTMLDivElement | null }>) {
    const t = useT();
    const state = useGame(gameId);
    const [confirmOpen, setConfirmOpen] = useState(false);
    const [pending, setPending] = useState(false);
    const [failure, setFailure] = useState<string>();
    const phase = state.snapshot?.phase;
    const canLeave =
        state.snapshot &&
        (phase === 'WAITING' || phase === 'PLACEMENT') &&
        state.snapshot.allowedActions.includes('LEAVE');

    const leave = async () => {
        if (!navigator.onLine || state.connection !== 'connected') return;
        setPending(true);
        setFailure(undefined);
        setConfirmOpen(false);
        try {
            await leaveGame(gameId);
            navigate('/');
        } catch (error) {
            setFailure(error instanceof GatewayError ? error.problem.messageKey : 'problem.serviceUnavailable');
        } finally {
            setPending(false);
        }
    };

    return (
        <>
            {canLeave &&
                target &&
                createPortal(
                    <>
                        <Button
                            variant="danger"
                            disabled={pending || state.connection !== 'connected'}
                            onClick={() => {
                                setConfirmOpen(true);
                            }}
                        >
                            {t('leave.game')}
                        </Button>
                        <ConfirmDialog
                            open={confirmOpen}
                            title={t('leave.title')}
                            message={t('leave.warning')}
                            confirmLabel={t('leave.game')}
                            confirmDisabled={pending || state.connection !== 'connected'}
                            onConfirm={() => void leave()}
                            onClose={() => {
                                setConfirmOpen(false);
                            }}
                        />
                    </>,
                    target,
                )}

            {failure && (
                <p role="alert">
                    {t(failure)}{' '}
                    <Button disabled={pending || state.connection !== 'connected'} onClick={() => void leave()}>
                        {t('leave.retry')}
                    </Button>
                </p>
            )}
        </>
    );
}
