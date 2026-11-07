import { useState } from 'react';
import { navigate } from '../app/router';
import { Button } from '../components/Button';
import { GatewayError, leaveGame } from '../gateway/gameGateway';
import { useT } from '../i18n/useT';
import type { GameId } from '../session/gameStore';
import { useGame } from '../session/useGame';
import type { MessageKey } from '../i18n/translate';
import './StatusScreen.css';

export function Abandoned({ gameId }: Readonly<{ gameId: GameId }>) {
    const t = useT();
    const state = useGame(gameId);
    const [pending, setPending] = useState(false);
    const [failure, setFailure] = useState<MessageKey>();
    const leaveResults = async () => {
        setPending(true);
        setFailure(undefined);
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
        <section className="home-screen">
            <div className="status-screen-content">
                <h1>{t('abandoned.title')}</h1>
                <p>{t('abandoned.message')}</p>
                {failure && <p role="alert">{t(failure)}</p>}
                <Button
                    variant="primary"
                    disabled={
                        pending || state.connection !== 'connected' || !state.snapshot?.allowedActions.includes('LEAVE')
                    }
                    onClick={() => void leaveResults()}
                >
                    {t('results.leave')}
                </Button>
                {failure && (
                    <Button disabled={pending || state.connection !== 'connected'} onClick={() => void leaveResults()}>
                        {t('leave.retry')}
                    </Button>
                )}
            </div>
        </section>
    );
}
