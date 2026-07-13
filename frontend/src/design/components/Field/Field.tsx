import {type InputHTMLAttributes, useId} from 'react';
import {Input} from '../Input/Input';
import './Field.css';

export type FieldProps = InputHTMLAttributes<HTMLInputElement> & {
    label: string;
    error?: string;
    fieldId?: string;
};

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
