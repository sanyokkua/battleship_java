import type { ButtonHTMLAttributes } from 'react';
import './Button.css';

type Props = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'ghost' | 'danger' };

export function Button({ variant = 'secondary', className = '', ...props }: Props) {
    return <button className={`btn btn-${variant} ${className}`.trim()} {...props} />;
}
