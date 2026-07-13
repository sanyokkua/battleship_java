import type {ReactNode} from 'react';
import './Card.css';

export type CardProps = {
    /** Card content. */
    children?: ReactNode;
    /** Additional class name(s) merged with the base `card` class. */
    className?: string;
};

/**
 * Design-system container. Renders a `<div className="card">` (plus any extra
 * `className`) around its children — the generic surface used to group content
 * throughout the app.
 */
export function Card({children, className}: CardProps) {
    const classes = ['card', className].filter(Boolean).join(' ');

    return <div className={classes}>{children}</div>;
}
