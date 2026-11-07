import { useState } from 'react';
import { navigate } from '../app/router';
import { Button } from '../components/Button';
import { GatewayError, joinGame } from '../gateway/gameGateway';
import type { MessageKey } from '../i18n/translate';
import { useT } from '../i18n/useT';
import { readCurrentGame, rememberCurrentGame } from '../session/currentGame';
import { clearInvitationSecret, readInvitationSecret } from '../session/inviteSecret';
import { SessionUnavailable } from './SessionUnavailable';
import './JoinInvitation.css';

const JOIN_TIMEOUT_MS = 10_000;

export function JoinInvitation({ gameId, offline = false }: { readonly gameId: string; readonly offline?: boolean }) {
    const t = useT();
    const [secret, setSecret] = useState(readInvitationSecret);
    const [name, setName] = useState('');
    const [pending, setPending] = useState(false);
    const [error, setError] = useState<MessageKey | ''>('');
    const [retryAfterSeconds, setRetryAfterSeconds] = useState(0);
    const [rememberedGame] = useState(readCurrentGame);
    const [expired, setExpired] = useState(false);

    function consumeSecret() {
        clearInvitationSecret();
        setSecret(undefined);
    }

    async function join() {
        if (offline || pending || !secret || !name.trim()) return;
        setPending(true);
        setError('');
        try {
            const snapshot = await joinGame(
                gameId,
                { invitationSecret: secret, displayName: name.trim() },
                AbortSignal.timeout(JOIN_TIMEOUT_MS),
            );
            consumeSecret();
            navigate(`/games/${encodeURIComponent(snapshot.gameId)}`);
        } catch (cause) {
            if (cause instanceof GatewayError) {
                if (cause.problem.status === 410 && cause.problem.code === 'game-expired') {
                    consumeSecret();
                    rememberCurrentGame(undefined);
                    setExpired(true);
                } else if (cause.problem.code === 'invitation-unavailable') {
                    consumeSecret();
                    setError('problem.invitationUnavailable');
                } else {
                    // Any other refusal leaves the invitation usable, so the secret stays for a retry.
                    setError(cause.problem.messageKey);
                    if (cause.problem.retryAfterSeconds) setRetryAfterSeconds(cause.problem.retryAfterSeconds);
                }
            } else {
                // The outcome is unknown: the join may have succeeded. Keep the secret and say so.
                setError('join.unknown');
            }
        } finally {
            setPending(false);
        }
    }

    if (expired) return <SessionUnavailable expired />;
    if (error === 'problem.invitationUnavailable') {
        return (
            <section className="home-screen">
                <div className="join-content">
                    <h1>{t(error)}</h1>
                    <Button
                        variant="primary"
                        onClick={() => {
                            navigate('/');
                        }}
                    >
                        {t('app.returnHome')}
                    </Button>
                </div>
            </section>
        );
    }

    return (
        <section className="home-screen">
            <div className="join-content">
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
            </div>
        </section>
    );
}
