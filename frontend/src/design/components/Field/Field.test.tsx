import {describe, expect, it} from 'vitest';
import {render, screen} from '@testing-library/react';
import {Field} from './Field';

describe('Field', () => {
    it('associates the label with the input via htmlFor/id', () => {
        render(<Field label="Player name" value="Superman" readOnly onChange={() => {
        }}/>);
        const input = screen.getByLabelText('Player name');
        expect(input).toHaveValue('Superman');
    });

    it('renders no error message when error is absent', () => {
        render(<Field label="Player name"/>);
        expect(screen.queryByText(/./, {selector: '.field-error'})).not.toBeInTheDocument();
        expect(screen.getByLabelText('Player name')).not.toHaveAttribute('aria-invalid');
    });

    it('shows the error text and marks the input errored', () => {
        render(<Field label="Player name" error="Name must be at least 2 characters."/>);
        expect(screen.getByText('Name must be at least 2 characters.')).toBeInTheDocument();
        const input = screen.getByLabelText('Player name');
        expect(input).toHaveAttribute('aria-invalid', 'true');
        expect(input).toHaveClass('err');
    });

    it('sets aria-describedby to the error message id', () => {
        render(<Field label="Player name" error="Too short" fieldId="player-name"/>);
        const input = screen.getByLabelText('Player name');
        const describedBy = input.getAttribute('aria-describedby');
        expect(describedBy).toBe('player-name-error');
        expect(document.getElementById(describedBy!)).toHaveTextContent('Too short');
    });

    it('honors an explicit fieldId', () => {
        render(<Field label="Game ID" fieldId="game-id"/>);
        expect(screen.getByLabelText('Game ID')).toHaveAttribute('id', 'game-id');
    });
});
