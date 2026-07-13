import type {InputHTMLAttributes} from 'react';
import './Input.css';

export type InputProps = InputHTMLAttributes<HTMLInputElement> & {
    error?: boolean;
};

export function Input({error, className, ...rest}: InputProps) {
    const classes = ['input', error ? 'err' : '', className].filter(Boolean).join(' ');

    return <input className={classes} {...rest} />;
}
