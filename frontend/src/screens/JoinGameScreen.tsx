import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useGameAdapter } from '../adapters/GameAdapterContext';
import { GameAdapterError, isGameAdapterError } from '../adapters/AdapterErrors';
import { saveSession, savePlayer, saveStage } from '../services/GameBrowserStorage';
import { isValidString, isValidUuid } from '../utils/StringUtils';
import { Field } from '../design/components/Field/Field';
import { Button } from '../design/components/Button/Button';
import { useToastContext } from '../widgets/feedback/ToastContext';
import { resolveErrorMessageKey } from '../widgets/feedback/errorMapping';
import './JoinGameScreen.css';

export function JoinGameScreen() {
  const { t } = useTranslation(['screens', 'notifications', 'errors']);
  const navigate = useNavigate();
  const adapter = useGameAdapter();
  const { push } = useToastContext();

  const [name, setName] = useState('');
  const [nameTouched, setNameTouched] = useState(false);
  const [gameId, setGameId] = useState('');
  const [gameIdTouched, setGameIdTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const nameValid = isValidString(name);
  const showNameError = nameTouched && !nameValid;
  const gameIdValid = isValidUuid(gameId);
  const showGameIdError = gameIdTouched && !gameIdValid;
  const canSubmit = nameValid && gameIdValid && !submitting;

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setNameTouched(true);
    setGameIdTouched(true);

    if (!nameValid || !gameIdValid || submitting) {
      if (!gameIdValid) {
        push({
          variant: 'err',
          title: t('notifications:join.invalidId.title'),
          message: t('notifications:join.invalidId.message'),
        });
      }
      return;
    }

    setSubmitting(true);
    try {
      const player = await adapter.createPlayer(gameId, name);
      const stage = await adapter.getStage(gameId);
      saveSession(gameId);
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

  return (
    <div className="screen">
      <div className="join-game-screen">
        <h2 className="title">{t('screens:join.title')}</h2>
        <p className="sub">{t('screens:join.subtitle')}</p>
        <form className="card stack" onSubmit={handleSubmit}>
          <Field
            label={t('screens:newGame.nameLabel')}
            value={name}
            onChange={(e) => setName(e.target.value)}
            onBlur={() => setNameTouched(true)}
            error={showNameError ? t('screens:validation.nameTooShort') : undefined}
          />
          <div>
            <Field
              label={t('screens:join.idLabel')}
              value={gameId}
              onChange={(e) => setGameId(e.target.value)}
              onBlur={() => setGameIdTouched(true)}
              error={showGameIdError ? t('notifications:join.invalidId.message') : undefined}
            />
            {gameIdValid && (
              <div className="field-valid">
                <span aria-hidden="true">✓</span>
                {t('screens:join.idValid')}
              </div>
            )}
          </div>
          <Button type="submit" variant="ok" disabled={!canSubmit}>
            {t('screens:join.submitButton')}
          </Button>
        </form>
      </div>
    </div>
  );
}
