import type { KeyboardEvent } from 'react';
import type { Ruleset } from '../gateway/gameGateway';
import { useT } from '../i18n/useT';
import { RulesetFacts } from './RulesetFacts';
import './RulesetCard.css';

export function RulesetCard({
    ruleset,
    selected,
    onSelect,
}: {
    ruleset: Ruleset;
    selected: boolean;
    onSelect: () => void;
}) {
    const t = useT();
    const onKeyDown = (event: KeyboardEvent<HTMLElement>) => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onSelect();
        }
    };
    return (
        <article
            role="radio"
            aria-checked={selected}
            tabIndex={0}
            className={`ruleset-card${selected ? ' selected' : ''}`}
            onClick={onSelect}
            onKeyDown={onKeyDown}
        >
            <h2>{t(`ruleset.${ruleset.id}`)}</h2>
            <RulesetFacts ruleset={ruleset} />
        </article>
    );
}
