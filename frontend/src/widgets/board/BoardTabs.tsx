import './BoardTabs.css';

export type BoardTabsProps = {
  active: 'target' | 'fleet';
  onChange: (tab: 'target' | 'fleet') => void;
  targetLabel: string;
  fleetLabel: string;
};

export function BoardTabs({ active, onChange, targetLabel, fleetLabel }: BoardTabsProps) {
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
