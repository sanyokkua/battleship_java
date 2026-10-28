import type { components } from '../contract/generated/api';
import { useT } from '../i18n/useT';
import './TurnPill.css';

export function TurnPill({ turn }: { turn: components['schemas']['Side'] | undefined }) {
    const t = useT();
    return <p className="turn-pill">{t(turn === 'YOU' ? 'play.yourTurn' : 'play.opponentTurn')}</p>;
}
