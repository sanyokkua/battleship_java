import type {InputHTMLAttributes} from 'react';
import './Input.css';

export type InputProps = InputHTMLAttributes<HTMLInputElement> & {
    /** When true, applies the `err` class to visually mark the input as invalid. */
    error?: boolean;
};

/**
 * Design-system text input. Renders a native `<input>` with the base `input`
 * class, adding `err` when {@link InputProps.error} is true, merged with any
 * `className` passed in. All other native input attributes are forwarded via `...rest`.
 */
export function Input({error, className, ...rest}: InputProps) {
    const classes = ['input', error ? 'err' : '', className].filter(Boolean).join(' ');

    return <input className={classes} {...rest} />;
}
