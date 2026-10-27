import type { Ruleset } from '../gateway/gameGateway';
import { useT } from '../i18n/useT';
import './RulesetFacts.css';

export function RulesetFacts({ ruleset }: { ruleset: Ruleset }) {
    const t = useT();
    const fleetText = ruleset.fleet
        .map((entry) => {
            const ship = entry.shipTypeId.startsWith('ship-')
                ? `${String(entry.length)} ${t('rules.cellShip')}`
                : t(`ship.${entry.shipTypeId}`);
            return `${String(entry.count)} × ${ship} (${String(entry.length)})`;
        })
        .join(', ');
    const facts = [
        [t('rules.field'), `${String(ruleset.board.columns)} × ${String(ruleset.board.rows)}`],
        [t('rules.fleet'), fleetText],
        [t('rules.contact'), ruleset.shipsMayTouch ? t('common.yes') : t('common.no')],
        [t('rules.extraTurn'), ruleset.extraTurnOnHit ? t('common.yes') : t('common.no')],
        [t('rules.revealedWater'), ruleset.revealWaterAroundSunk ? t('common.yes') : t('common.no')],
    ];
    return (
        <dl className="rules-facts">
            {facts.map(([label, value]) => (
                <div key={label}>
                    <dt>{label}</dt>
                    <dd>{value}</dd>
                </div>
            ))}
        </dl>
    );
}
