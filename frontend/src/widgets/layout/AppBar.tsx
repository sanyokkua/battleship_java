import type {MouseEvent as ReactMouseEvent} from 'react';
import {useState} from 'react';
import {NavLink, useNavigate} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {useSessionGuard} from '../../hooks/useSessionGuard';
import {clearGameData} from '../../services/GameBrowserStorage';
import {ConfirmDialog} from '../feedback/ConfirmDialog';
import './AppBar.css';

type NavItem = {
    to: string;
    labelKey: string;
};

const ALWAYS_VISIBLE_LINKS: NavItem[] = [
    {to: '/', labelKey: 'nav.home'},
    {to: '/new', labelKey: 'nav.new'},
    {to: '/join', labelKey: 'nav.join'},
];

const IN_GAME_LINKS: NavItem[] = [
    {to: '/game/preparation', labelKey: 'nav.prep'},
    {to: '/game/gameplay', labelKey: 'nav.gameplay'},
];

// Stages during which navigating away should prompt a "leave this game?" confirmation.
const LEAVE_CONFIRM_STAGES = new Set(['PREPARATION', 'IN_GAME']);

export function AppBar() {
    const {t, i18n} = useTranslation('common');
    const {sessionId, player, stage} = useSessionGuard();
    const navigate = useNavigate();
    const [menuOpen, setMenuOpen] = useState(false);
    const [pendingDestination, setPendingDestination] = useState<string | null>(null);

    const hasActiveSession = Boolean(sessionId && player);
    const shouldConfirmLeave = hasActiveSession && stage !== null && LEAVE_CONFIRM_STAGES.has(stage);

    const navLinks = hasActiveSession ? [...ALWAYS_VISIBLE_LINKS, ...IN_GAME_LINKS] : ALWAYS_VISIBLE_LINKS;

    function handleNavClick(event: ReactMouseEvent<HTMLAnchorElement>, to: string) {
        if (!shouldConfirmLeave) {
            setMenuOpen(false);
            return;
        }
        event.preventDefault();
        setPendingDestination(to);
    }

    function handleCancelLeave() {
        setPendingDestination(null);
    }

    function handleConfirmLeave() {
        const destination = pendingDestination;
        clearGameData();
        setPendingDestination(null);
        setMenuOpen(false);
        if (destination) {
            navigate(destination);
        }
    }

    function changeLanguage(lang: 'en' | 'uk') {
        void i18n.changeLanguage(lang);
    }

    const activeLanguage = i18n.language?.startsWith('uk') ? 'uk' : 'en';

    return (
        <header className="appbar">
            <div className="l">
        <span className="anchor" aria-hidden="true">
          ⚓
        </span>
                {t('appName')}
            </div>
            <button
                type="button"
                className="burger"
                aria-label={t('nav.menu')}
                aria-expanded={menuOpen}
                onClick={() => setMenuOpen((open) => !open)}
            >
                ☰
            </button>
            <nav className={`r${menuOpen ? ' open' : ''}`}>
                {navLinks.map((link) => (
                    <NavLink
                        key={link.to}
                        to={link.to}
                        end={link.to === '/'}
                        className={({isActive}) => `navlink${isActive ? ' on' : ''}`}
                        onClick={(event) => handleNavClick(event, link.to)}
                    >
                        {t(link.labelKey)}
                    </NavLink>
                ))}
                <span className="lang" role="group" aria-label={t('nav.languageSwitch')}>
          <button
              type="button"
              className={activeLanguage === 'en' ? 'on' : ''}
              aria-pressed={activeLanguage === 'en'}
              onClick={() => changeLanguage('en')}
          >
            EN
          </button>
          <button
              type="button"
              className={activeLanguage === 'uk' ? 'on' : ''}
              aria-pressed={activeLanguage === 'uk'}
              onClick={() => changeLanguage('uk')}
          >
            УКР
          </button>
        </span>
            </nav>
            <ConfirmDialog
                open={pendingDestination !== null}
                icon="🚪"
                title={t('screens:dialog.leaveGame.title')}
                body={t('screens:dialog.leaveGame.body')}
                cancelLabel={t('screens:dialog.leaveGame.cancel')}
                confirmLabel={t('screens:dialog.leaveGame.confirm')}
                onCancel={handleCancelLeave}
                onConfirm={handleConfirmLeave}
            />
        </header>
    );
}
