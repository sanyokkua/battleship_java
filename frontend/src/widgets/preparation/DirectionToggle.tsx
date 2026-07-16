import type {ShipDirection} from '../../logic/ApplicationTypes';
import './DirectionToggle.css';

export type DirectionToggleProps = {
    direction: ShipDirection;
    onChange: (d: ShipDirection) => void;
    horizontalLabel: string;
    verticalLabel: string;
};

/**
 * Inline Horizontal/Vertical placement-direction toggle: two buttons, the
 * active one gets the `.on` class. Labels are supplied by the caller (already
 * localized) — this widget never calls useTranslation() itself, per this
 * project's widget convention.
 */
export function DirectionToggle({direction, onChange, horizontalLabel, verticalLabel}: DirectionToggleProps) {
    return (
        <div className="dir-toggle">
            <button
                type="button"
                className={direction === 'HORIZONTAL' ? 'on' : ''}
                aria-pressed={direction === 'HORIZONTAL'}
                onClick={() => onChange('HORIZONTAL')}
            >
                <span aria-hidden="true">↔</span> {horizontalLabel}
            </button>
            <button
                type="button"
                className={direction === 'VERTICAL' ? 'on' : ''}
                aria-pressed={direction === 'VERTICAL'}
                onClick={() => onChange('VERTICAL')}
            >
                <span aria-hidden="true">↕</span> {verticalLabel}
            </button>
        </div>
    );
}
