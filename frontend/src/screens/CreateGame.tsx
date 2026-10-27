import { useEffect, useState } from 'react';
import type { components } from '../contract/generated/api';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { RulesetCard } from '../components/RulesetCard';
import { GatewayError, createGame, listRulesets, type Ruleset } from '../gateway/gameGateway';
import { TransportFailure } from '../gateway/decode';
import { useT } from '../i18n/useT';
import { navigate } from '../app/router';
import { readCurrentGame } from '../session/currentGame';
import { RulesDialog } from './RulesDialog';

export function CreateGame() {
    const t = useT();
    const [rulesets, setRulesets] = useState<Ruleset[]>([]);
    const [selected, setSelected] = useState('');
    const [name, setName] = useState('');
    const [pending, setPending] = useState(false);
    const [error, setError] = useState('');
    const [waitUntil, setWaitUntil] = useState(0);
    const [waitActive, setWaitActive] = useState(false);
    const [retryAfterSeconds, setRetryAfterSeconds] = useState(0);
    const [rememberedGame] = useState(readCurrentGame);
    const [rulesOpen, setRulesOpen] = useState(false);
    useEffect(() => {
        void listRulesets()
            .then((items) => {
                setRulesets(items);
                setSelected(items[0]?.id ?? '');
            })
            .catch(() => {
                setError('problem.serviceUnavailable');
            });
    }, []);
    useEffect(() => {
        if (!waitUntil) return;
        const timer = window.setInterval(() => {
            if (Date.now() >= waitUntil) {
                setWaitUntil(0);
                setWaitActive(false);
                setRetryAfterSeconds(0);
                setError('');
            }
        }, 250);
        return () => {
            window.clearInterval(timer);
        };
    }, [waitUntil]);
    async function submit() {
        if (pending || !selected || Date.now() < waitUntil || name.trim().length < 1) return;
        setPending(true);
        setError('');
        try {
            const snapshot = await createGame({
                rulesetId: selected,
                displayName: name.trim(),
            } satisfies components['schemas']['CreateGameRequest']);
            navigate(`/games/${encodeURIComponent(snapshot.gameId)}`, { toast: 'game-created' });
        } catch (cause) {
            if (cause instanceof GatewayError) {
                setError(cause.problem.messageKey);
                if (cause.problem.retryAfterSeconds) {
                    setWaitUntil(Date.now() + cause.problem.retryAfterSeconds * 1000);
                    setWaitActive(true);
                    setRetryAfterSeconds(cause.problem.retryAfterSeconds);
                }
            } else if (cause instanceof TransportFailure || cause instanceof TypeError) setError('create.unknown');
            else setError('problem.serviceUnavailable');
        } finally {
            setPending(false);
        }
    }
    return (
        <section className="home-screen">
            <Card className="card-pad create-card">
                <h1>{t('create.title')}</h1>
                <div role="radiogroup" aria-label={t('ruleset.label')} className="ruleset-list">
                    {rulesets.map((ruleset) => (
                        <RulesetCard
                            key={ruleset.id}
                            ruleset={ruleset}
                            selected={selected === ruleset.id}
                            onSelect={() => {
                                setSelected(ruleset.id);
                            }}
                        />
                    ))}
                </div>
                <label htmlFor="captain-name">{t('create.name')}</label>
                <input
                    id="captain-name"
                    aria-label={t('create.name')}
                    maxLength={32}
                    value={name}
                    onChange={(event) => {
                        setName(event.target.value);
                    }}
                />
                <p>{t('create.nameHelp')}</p>
                {error && (
                    <p role="alert">
                        {t(error)}
                        {retryAfterSeconds > 0 &&
                            ` ${t('problem.retryAfter').replace('{seconds}', String(retryAfterSeconds))}`}
                    </p>
                )}
                <div className="create-actions">
                    <Button
                        onClick={() => {
                            navigate('/');
                        }}
                    >
                        {t('create.back')}
                    </Button>
                    <Button
                        onClick={() => {
                            setRulesOpen(true);
                        }}
                    >
                        {t('create.details')}
                    </Button>
                    <Button
                        variant="primary"
                        disabled={pending || !selected || !name.trim() || waitActive}
                        onClick={() => void submit()}
                    >
                        {t('create.submit')}
                    </Button>
                </div>
                {error === 'create.unknown' && (
                    <Button
                        onClick={() => {
                            navigate('/');
                        }}
                    >
                        {t('create.returnHome')}
                    </Button>
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
            </Card>
            <RulesDialog
                open={rulesOpen}
                onClose={() => {
                    setRulesOpen(false);
                }}
            />
        </section>
    );
}
