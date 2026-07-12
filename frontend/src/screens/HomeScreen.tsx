import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Button } from '../design/components/Button/Button';
import './HomeScreen.css';

export function HomeScreen() {
  const { t } = useTranslation('screens');
  const { t: tCommon } = useTranslation('common');
  const navigate = useNavigate();

  return (
    <div className="screen">
      <div className="home-menu">
        <div className="hero">
          <div className="badge-ship" aria-hidden="true">
            🚢
          </div>
          <h2>{tCommon('appName')}</h2>
          <p>{t('home.tagline')}</p>
        </div>
        <div className="menu-btns">
          <Button variant="primary" onClick={() => navigate('/new')}>
            {t('home.newGameButton')}
          </Button>
          <Button variant="ghost" onClick={() => navigate('/join')}>
            {t('home.joinGameButton')}
          </Button>
        </div>
        <p className="note">{t('home.note')}</p>
      </div>
    </div>
  );
}
