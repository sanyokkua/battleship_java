import type {ReactNode} from 'react';
import './Pill.css';

/** Visual style of a {@link Pill} — selects the color modifier class. */
export type PillVariant = 'warn' | 'ok' | 'turn';

export type PillProps = {
    /** Visual style applied to the pill. */
    variant: PillVariant;
    /** Pill content (typically short status text). */
    children: ReactNode;
};

/**
 * Small rounded status badge. Renders a `<span>` with `pill` plus the
 * {@link PillProps.variant} class, used to call out short status text
 * (e.g. turn indicator, warning, success state).
 */
export function Pill({variant, children}: PillProps) {
    return <span className={`pill ${variant}`}>{children}</span>;
}
