import type { KeyboardEvent } from 'react';
import type { Ruleset } from '../gateway/gameGateway';
import { useT } from '../i18n/useT';
import './RulesetCard.css';

export function RulesetCard({
    ruleset,
    selected,
    onSelect,
}: Readonly<{
    ruleset: Ruleset;
    selected: boolean;
    onSelect: () => void;
}>) {
    const t = useT();
    const onKeyDown = (event: KeyboardEvent<HTMLElement>) => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onSelect();
        }
    };
    const counts = new Map<number, number>();
    for (const ship of ruleset.fleet) counts.set(ship.length, (counts.get(ship.length) ?? 0) + ship.count);
    return (
        <div
            role="radio"
            aria-checked={selected}
            tabIndex={0}
            className={`ruleset-card${selected ? ' selected' : ''}`}
            onClick={onSelect}
            onKeyDown={onKeyDown}
        >
            <h2>{t(`ruleset.${ruleset.id}`)}</h2>
            <div className="ruleset-badges">
                {[...counts]
                    .sort(([a], [b]) => a - b)
                    .map(([length, count]) => (
                        <span key={length}>
                            {count} × {length}
                        </span>
                    ))}
            </div>
        </div>
    );
}
