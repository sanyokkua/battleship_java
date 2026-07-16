import {describe, expect, it, vi} from 'vitest';
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {DirectionToggle} from './DirectionToggle';

describe('DirectionToggle', () => {
    it('renders both direction buttons with the supplied labels', () => {
        render(<DirectionToggle direction="HORIZONTAL" onChange={() => {
        }} horizontalLabel="Horizontal" verticalLabel="Vertical"/>);

        expect(screen.getByRole('button', {name: /Horizontal/})).toBeInTheDocument();
        expect(screen.getByRole('button', {name: /Vertical/})).toBeInTheDocument();
    });

    it('marks the Horizontal button active (class + aria-pressed) when direction is HORIZONTAL', () => {
        render(<DirectionToggle direction="HORIZONTAL" onChange={() => {
        }} horizontalLabel="Horizontal" verticalLabel="Vertical"/>);

        const horizontal = screen.getByRole('button', {name: /Horizontal/});
        const vertical = screen.getByRole('button', {name: /Vertical/});

        expect(horizontal).toHaveClass('on');
        expect(horizontal).toHaveAttribute('aria-pressed', 'true');
        expect(vertical).not.toHaveClass('on');
        expect(vertical).toHaveAttribute('aria-pressed', 'false');
    });

    it('marks the Vertical button active (class + aria-pressed) when direction is VERTICAL', () => {
        render(<DirectionToggle direction="VERTICAL" onChange={() => {
        }} horizontalLabel="Horizontal" verticalLabel="Vertical"/>);

        const horizontal = screen.getByRole('button', {name: /Horizontal/});
        const vertical = screen.getByRole('button', {name: /Vertical/});

        expect(vertical).toHaveClass('on');
        expect(vertical).toHaveAttribute('aria-pressed', 'true');
        expect(horizontal).not.toHaveClass('on');
        expect(horizontal).toHaveAttribute('aria-pressed', 'false');
    });

    it('calls onChange with HORIZONTAL when the Horizontal button is clicked', async () => {
        const user = userEvent.setup();
        const onChange = vi.fn();
        render(<DirectionToggle direction="VERTICAL" onChange={onChange} horizontalLabel="Horizontal"
                                verticalLabel="Vertical"/>);

        await user.click(screen.getByRole('button', {name: /Horizontal/}));

        expect(onChange).toHaveBeenCalledTimes(1);
        expect(onChange).toHaveBeenCalledWith('HORIZONTAL');
    });

    it('calls onChange with VERTICAL when the Vertical button is clicked', async () => {
        const user = userEvent.setup();
        const onChange = vi.fn();
        render(<DirectionToggle direction="HORIZONTAL" onChange={onChange} horizontalLabel="Horizontal"
                                verticalLabel="Vertical"/>);

        await user.click(screen.getByRole('button', {name: /Vertical/}));

        expect(onChange).toHaveBeenCalledTimes(1);
        expect(onChange).toHaveBeenCalledWith('VERTICAL');
    });

    it('still fires onChange when clicking the already-active direction button', async () => {
        const user = userEvent.setup();
        const onChange = vi.fn();
        render(<DirectionToggle direction="HORIZONTAL" onChange={onChange} horizontalLabel="Horizontal"
                                verticalLabel="Vertical"/>);

        await user.click(screen.getByRole('button', {name: /Horizontal/}));

        expect(onChange).toHaveBeenCalledTimes(1);
        expect(onChange).toHaveBeenCalledWith('HORIZONTAL');
    });
});
