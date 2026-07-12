import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useGameAdapter } from '../adapters/GameAdapterContext';
import { GameAdapterError, isGameAdapterError } from '../adapters/AdapterErrors';
import { saveSession, savePlayer, saveStage } from '../services/GameBrowserStorage';
import { isValidString } from '../utils/StringUtils';
import { getEditionLabel, getEditionDescription } from '../i18n-support/editionNames';
import { EDITION_COMPOSITIONS } from '../i18n-support/editionCompositions';
import { ModeCard } from '../design/components/ModeCard/ModeCard';
import { Field } from '../design/components/Field/Field';
import { Button } from '../design/components/Button/Button';
import { LoadingView } from '../widgets/layout/LoadingView';
import { useToastContext } from '../widgets/feedback/ToastContext';
import { resolveErrorMessageKey } from '../widgets/feedback/errorMapping';
import './NewGameScreen.css';

/** Icon shown per edition on its mode card. No icon field exists on the DTO/i18n side, so it's a small local lookup keyed by the same edition literal used everywhere else. */
const EDITION_ICONS: Record<string, string> = {
  UKRAINIAN: '🇺🇦',
  MILTON_BRADLEY: '🎲',
};

function shipSizesFor(edition: string): number[] {
  const composition = EDITION_COMPOSITIONS[edition] ?? [];
  const sizes: number[] = [];
  for (const entry of composition) {
    for (let i = 0; i < entry.count; i++) {
      sizes.push(entry.size);
    }
  }
  return sizes;
}

export function NewGameScreen() {
  const { t } = useTranslation(['screens', 'common', 'notifications', 'errors']);
  const navigate = useNavigate();
  const adapter = useGameAdapter();
  const { push } = useToastContext();

  const [editions, setEditions] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedEdition, setSelectedEdition] = useState<string>('');
  const [name, setName] = useState('');
  const [nameTouched, setNameTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function loadEditions() {
      setLoading(true);
      try {
        const result = await adapter.getEditions();
        if (cancelled) return;
        setEditions(result);
        setSelectedEdition((current) => current || result[0] || '');
      } catch (err) {
        if (cancelled) return;
        const adapterErr = isGameAdapterError(err)
          ? err
          : new GameAdapterError('Request failed', { cause: err });
        push({
          variant: 'err',
          title: t('notifications:error.generic.title'),
          message: t(`errors:${resolveErrorMessageKey(adapterErr)}`),
        });
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void loadEditions();

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [adapter]);

  const nameValid = isValidString(name);
  const showNameError = nameTouched && !nameValid;
  const canSubmit = nameValid && Boolean(selectedEdition) && !submitting;

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setNameTouched(true);
    if (!nameValid || !selectedEdition || submitting) return;

    setSubmitting(true);
    try {
      const sessionId = await adapter.createSession(selectedEdition);
      const player = await adapter.createPlayer(sessionId, name);
      const stage = await adapter.getStage(sessionId);
      saveSession(sessionId);
      savePlayer(player);
      saveStage(stage);
      navigate('/game/wait');
    } catch (err) {
      const adapterErr = isGameAdapterError(err)
        ? err
        : new GameAdapterError('Request failed', { cause: err });
      push({
        variant: 'err',
        title: t('notifications:error.generic.title'),
        message: t(`errors:${resolveErrorMessageKey(adapterErr)}`),
      });
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return <LoadingView title={t('screens:loading.title')} subtitle={t('screens:loading.subtitle')} />;
  }

  return (
    <div className="screen">
      <div className="new-game-screen">
        <h2 className="title">{t('screens:newGame.title')}</h2>
        <p className="sub">{t('screens:newGame.subtitle')}</p>
        <form className="card stack" onSubmit={handleSubmit}>
          <div>
            <label className="fld">{t('screens:newGame.modeLabel')}</label>
            <div className="mode-cards" role="radiogroup" aria-label={t('screens:newGame.modeLabel')}>
              {editions.map((edition) => (
                <ModeCard
                  key={edition}
                  icon={EDITION_ICONS[edition] ?? '🚢'}
                  name={getEditionLabel(edition, t)}
                  description={getEditionDescription(edition, t)}
                  shipSizes={shipSizesFor(edition)}
                  selected={selectedEdition === edition}
                  onSelect={() => setSelectedEdition(edition)}
                />
              ))}
            </div>
          </div>
          <Field
            label={t('screens:newGame.nameLabel')}
            value={name}
            onChange={(e) => setName(e.target.value)}
            onBlur={() => setNameTouched(true)}
            error={showNameError ? t('screens:validation.nameTooShort') : undefined}
          />
          <Button type="submit" variant="primary" disabled={!canSubmit}>
            {t('screens:newGame.startButton')}
          </Button>
        </form>
        <p className="note">{t('screens:newGame.hint')}</p>
      </div>
    </div>
  );
}
