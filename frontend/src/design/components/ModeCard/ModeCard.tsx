import type {ReactNode} from 'react';
import './ModeCard.css';

export type ModeCardProps = {
    icon: ReactNode;
    name: string;
    description: string;
    shipSizes: number[];
    selected: boolean;
    onSelect: () => void;
};

export function ModeCard({icon, name, description, shipSizes, selected, onSelect}: ModeCardProps) {
    return (
        <button
            type="button"
            role="radio"
            aria-checked={selected}
            aria-pressed={selected}
            className={`mode-card${selected ? ' on' : ''}`}
            onClick={onSelect}
        >
            <div className="mc-top">
        <span className="mc-name">
          {icon} {name}
        </span>
                <span className="mc-check">{selected ? '✓' : ''}</span>
            </div>
            <div className="mc-desc">{description}</div>
            <div className="mc-ships">
                {shipSizes.map((size, index) => (
                    <i key={index} style={{width: `${size * 10}px`}}/>
                ))}
            </div>
        </button>
    );
}
