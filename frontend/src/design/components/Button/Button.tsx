import type {ButtonHTMLAttributes, ReactNode} from 'react';
import './Button.css';

export type ButtonVariant = 'primary' | 'ghost' | 'ok' | 'danger';

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
    variant?: ButtonVariant;
    size?: 'default' | 'sm';
    children?: ReactNode;
};

export function Button({
                           variant = 'primary',
                           size = 'default',
                           className,
                           children,
                           ...rest
                       }: ButtonProps) {
    const classes = ['btn', `btn-${variant}`, size === 'sm' ? 'sm' : '', className]
        .filter(Boolean)
        .join(' ');

    return (
        <button className={classes} {...rest}>
            {children}
        </button>
    );
}
