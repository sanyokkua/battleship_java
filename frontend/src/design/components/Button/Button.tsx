import type {ButtonHTMLAttributes, ReactNode} from 'react';
import './Button.css';

/** Visual style of a {@link Button} — selects the `btn-*` modifier class. */
export type ButtonVariant = 'primary' | 'ghost' | 'ok' | 'danger';

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
    /** Visual style applied to the button. Defaults to `'primary'`. */
    variant?: ButtonVariant;
    /** Size modifier. `'sm'` adds the compact `sm` class. Defaults to `'default'`. */
    size?: 'default' | 'sm';
    /** Button content. */
    children?: ReactNode;
};

/**
 * Design-system button. Renders a native `<button>` with `btn`/`btn-{variant}`/`sm`
 * classes derived from {@link ButtonProps.variant} and {@link ButtonProps.size}, merged
 * with any `className` passed in. All other native button attributes (`onClick`,
 * `disabled`, `type`, etc.) are forwarded via `...rest`.
 */
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
