import type { HTMLAttributes } from 'react';
import './Card.css';

export function Card({ className = '', ...props }: Readonly<HTMLAttributes<HTMLElement>>) {
    return <article className={`card ${className}`.trim()} {...props} />;
}
