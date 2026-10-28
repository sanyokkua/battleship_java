import type { components } from '../contract/generated/api';
import { useT } from '../i18n/useT';
import { columnLabel } from './Board';

export function Announcer({ shot }: { shot: components['schemas']['Shot'] | undefined }) {
    const t = useT();
    // React leaves the live-region text untouched when later snapshots repeat this shot.
    const message = shot
        ? `${t(shot.by === 'YOU' ? 'play.youFired' : 'play.opponentFired')} ${columnLabel(shot.target.columnIndex)}${String(shot.target.rowIndex + 1)}: ${t(`cell.${shot.result.toLowerCase()}`)}.`
        : '';
    return (
        <p className="shot-announcement" aria-live="polite" aria-atomic="true" aria-label={t('play.announcement')}>
            {message}
        </p>
    );
}
