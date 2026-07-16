import type {ReactNode} from 'react';
import './ModeCard.css';

export type ModeCardProps = {
    /** Icon rendered next to the mode name. */
    icon: ReactNode;
    /** Display name of the game edition/mode. */
    name: string;
    /** Short description shown below the name. */
    description: string;
    /** Ship sizes for this edition, rendered as a row of proportionally-sized bars. */
    shipSizes: number[];
    /** Whether this mode is the currently selected one. */
    selected: boolean;
    /** Called when the card is clicked/activated to select this mode. */
    onSelect: () => void;
};

/**
 * Selectable card representing one game edition/mode (e.g. Ukrainian, Milton
 * Bradley) in a radio-group-style picker. Renders as a `role="radio"` button
 * with `aria-checked`/`aria-pressed` reflecting {@link ModeCardProps.selected},
 * and visualizes {@link ModeCardProps.shipSizes} as a row of bars sized
 * proportionally to each ship's length.
 */
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
