import './BoardTabs.css';

export type BoardTabsProps = {
    active: 'target' | 'fleet';
    onChange: (tab: 'target' | 'fleet') => void;
    targetLabel: string;
    fleetLabel: string;
};

/**
 * Two-tab switcher (ARIA `tablist`/`tab`) between the "target" (opponent) and "fleet"
 * (own) boards during gameplay. Purely controlled — `active` selects the highlighted
 * tab and `onChange` is called with the tapped tab's id; rendering the corresponding
 * board is the caller's responsibility.
 */
export function BoardTabs({active, onChange, targetLabel, fleetLabel}: BoardTabsProps) {
    return (
        <div className="board-tabs" role="tablist">
            <button
                type="button"
                role="tab"
                aria-selected={active === 'target'}
                className={active === 'target' ? 'on' : ''}
                onClick={() => onChange('target')}
            >
                {targetLabel}
            </button>
            <button
                type="button"
                role="tab"
                aria-selected={active === 'fleet'}
                className={active === 'fleet' ? 'on' : ''}
                onClick={() => onChange('fleet')}
            >
                {fleetLabel}
            </button>
        </div>
    );
}
