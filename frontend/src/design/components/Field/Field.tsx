import {type InputHTMLAttributes, useId} from 'react';
import {Input} from '../Input/Input';
import './Field.css';

export type FieldProps = InputHTMLAttributes<HTMLInputElement> & {
    /** Visible label text rendered above the input. */
    label: string;
    /** Error message. When set, renders below the input and marks it invalid via `aria-invalid`/`aria-describedby`. */
    error?: string;
    /** Explicit id for the input/label pair. Falls back to a generated id (via `useId`) when omitted. */
    fieldId?: string;
};

/**
 * Labeled {@link Input} wrapper with built-in error display. Generates (or reuses
 * a caller-supplied) id to associate the `<label>` with the input, and wires up
 * `aria-invalid`/`aria-describedby` to the error message when {@link FieldProps.error}
 * is set. All other native input attributes are forwarded to the underlying {@link Input}.
 */
export function Field({label, error, fieldId, className, ...inputProps}: FieldProps) {
    const generatedId = useId();
    const id = fieldId ?? generatedId;
    const errorId = `${id}-error`;

    return (
        <div className={['fld-wrap', className].filter(Boolean).join(' ')}>
            <label className="fld" htmlFor={id}>
                {label}
            </label>
            <Input
                id={id}
                error={Boolean(error)}
                aria-invalid={error ? 'true' : undefined}
                aria-describedby={error ? errorId : undefined}
                {...inputProps}
            />
            {error && (
                <div className="field-error" id={errorId}>
                    <span aria-hidden="true">⚠️</span>
                    {error}
                </div>
            )}
        </div>
    );
}
