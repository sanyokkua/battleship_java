import { useState } from 'react';
import { navigate } from '../app/router';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { GatewayError, joinGame } from '../gateway/gameGateway';
import { TransportFailure } from '../gateway/decode';
import { useT } from '../i18n/useT';
import { readCurrentGame, rememberCurrentGame } from '../session/currentGame';
import { clearInvitationSecret, readInvitationSecret } from '../session/inviteSecret';
import { SessionUnavailable } from './SessionUnavailable';

export function JoinInvitation({ gameId, offline = false }: { gameId: string; offline?: boolean }) {
    const t = useT();
    const [secret, setSecret] = useState(readInvitationSecret);
    const [name, setName] = useState('');
    const [pending, setPending] = useState(false);
    const [error, setError] = useState('');
    const [retryAfterSeconds, setRetryAfterSeconds] = useState(0);
    const [rememberedGame] = useState(readCurrentGame);
    const [expired, setExpired] = useState(false);

    async function join() {
        if (offline || pending || !secret || !name.trim()) return;
        setPending(true);
        setError('');
        try {
            const snapshot = await joinGame(gameId, { invitationSecret: secret, displayName: name.trim() });
            navigate(`/games/${encodeURIComponent(snapshot.gameId)}`);
        } catch (cause) {
            if (cause instanceof GatewayError) {
                if (cause.problem.status === 410 && cause.problem.code === 'game-expired') {
                    rememberCurrentGame(undefined);
                    setExpired(true);
                } else if (cause.problem.code === 'invitation-unavailable') {
                    setError('problem.invitationUnavailable');
                } else {
                    setError(cause.problem.messageKey);
                    if (cause.problem.retryAfterSeconds) setRetryAfterSeconds(cause.problem.retryAfterSeconds);
                }
            } else if (cause instanceof TransportFailure || cause instanceof TypeError) {
                setError('problem.invitationUnavailable');
            } else {
                setError('problem.invitationUnavailable');
            }
        } finally {
            clearInvitationSecret();
            setSecret(undefined);
            setPending(false);
        }
    }

    if (expired) return <SessionUnavailable expired />;
    if (error === 'problem.invitationUnavailable') {
        return (
            <section className="home-screen">
                <Card className="card-pad">
                    <h1>{t(error)}</h1>
                    <Button
                        variant="primary"
                        onClick={() => {
                            navigate('/');
                        }}
                    >
                        {t('app.returnHome')}
                    </Button>
                </Card>
            </section>
        );
    }

    return (
        <section className="home-screen">
            <Card className="card-pad create-card">
                <h1>{t('join.title')}</h1>
                <label htmlFor="guest-name">{t('create.name')}</label>
                <input
                    id="guest-name"
                    maxLength={32}
                    value={name}
                    onChange={(event) => {
                        setName(event.target.value);
                    }}
                />
                {error && (
                    <p role="alert">
                        {t(error)}
                        {retryAfterSeconds > 0 &&
                            ` ${t('problem.retryAfter').replace('{seconds}', String(retryAfterSeconds))}`}
                    </p>
                )}
                {error === 'problem.browserGameLimit' && rememberedGame && (
                    <Button
                        onClick={() => {
                            navigate(`/games/${encodeURIComponent(rememberedGame)}`);
                        }}
                    >
                        {t('home.returnToGame')}
                    </Button>
                )}
                <Button
                    variant="primary"
                    disabled={offline || pending || !name.trim() || !secret}
                    onClick={() => void join()}
                >
                    {t('join.submit')}
                </Button>
            </Card>
        </section>
    );
}
