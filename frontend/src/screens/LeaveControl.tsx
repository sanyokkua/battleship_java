import { useState } from 'react';
import { navigate } from '../app/router';
import { Button } from '../components/Button';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { GatewayError, leaveGame } from '../gateway/gameGateway';
import { useT } from '../i18n/useT';
import type { GameId } from '../session/gameStore';
import { useGame } from '../session/useGame';

export function LeaveControl({ gameId }: { gameId: GameId }) {
    const t = useT();
    const state = useGame(gameId);
    const [confirmOpen, setConfirmOpen] = useState(false);
    const [pending, setPending] = useState(false);
    const [failure, setFailure] = useState<string>();
    const phase = state.snapshot?.phase;
    if (
        !state.snapshot ||
        (phase !== 'WAITING' && phase !== 'PLACEMENT') ||
        !state.snapshot.allowedActions.includes('LEAVE')
    )
        return null;

    const leave = async () => {
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
            <Button
                variant="danger"
                disabled={pending || state.connection !== 'connected'}
                onClick={() => {
                    setConfirmOpen(true);
                }}
            >
                {t('leave.game')}
            </Button>
            {failure && (
                <p role="alert">
                    {t(failure)}{' '}
                    <Button disabled={pending || state.connection !== 'connected'} onClick={() => void leave()}>
                        {t('leave.retry')}
                    </Button>
                </p>
            )}
            <ConfirmDialog
                open={confirmOpen}
                title={t('leave.title')}
                message={t('leave.warning')}
                confirmLabel={t('leave.game')}
                onConfirm={() => void leave()}
                onClose={() => {
                    setConfirmOpen(false);
                }}
            />
        </>
    );
}
