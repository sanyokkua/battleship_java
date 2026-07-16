import {describe, expect, it, vi} from 'vitest';
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type {CellDto} from '../../logic/ApplicationTypes';
import {BoardCell} from './BoardCell';

function cell(overrides: Partial<CellDto> = {}): CellDto {
    return {row: 2, col: 2, ship: null, hasShot: false, isAvailable: true, ...overrides};
}

describe('BoardCell', () => {
    it('renders water for an untouched cell with no ship', () => {
        render(<BoardCell cell={cell()} mode="own" sunk={false} isGhost={false}/>);
        expect(screen.getByLabelText('C3, water')).toBeInTheDocument();
    });

    it('renders your-ship in own mode for an unshot ship cell', () => {
        const c = cell({ship: {shipId: 's1', shipSize: 2}});
        render(<BoardCell cell={c} mode="own" sunk={false} isGhost={false}/>);
        expect(screen.getByLabelText('C3, your ship')).toBeInTheDocument();
    });

    it('suppresses ship reveal in target mode even if cell.ship is set and unshot', () => {
        const c = cell({ship: {shipId: 's1', shipSize: 2}, hasShot: false});
        render(<BoardCell cell={c} mode="target" sunk={false} isGhost={false}/>);
        expect(screen.getByLabelText('C3, water')).toBeInTheDocument();
    });

    it('renders ghost preview in prep mode, taking precedence over everything else', () => {
        const c = cell({ship: {shipId: 's1', shipSize: 2}});
        render(<BoardCell cell={c} mode="prep" sunk={false} isGhost={true}/>);
        expect(screen.getByLabelText('C3, valid drop')).toBeInTheDocument();
    });

    it('renders no-go/blocked in prep mode and does not fire onClick when clicked', async () => {
        const user = userEvent.setup();
        const onClick = vi.fn();
        const c = cell({isAvailable: false});
        render(<BoardCell cell={c} mode="prep" sunk={false} isGhost={false} onClick={onClick}/>);
        const el = screen.getByLabelText('C3, blocked');
        // Non-interactive: should not be a button
        expect(el.tagName).toBe('DIV');
        await user.click(el);
        expect(onClick).not.toHaveBeenCalled();
    });

    it('renders hit state for a shot cell with a ship that is not sunk', () => {
        const c = cell({ship: {shipId: 's1', shipSize: 2}, hasShot: true});
        render(<BoardCell cell={c} mode="target" sunk={false} isGhost={false}/>);
        expect(screen.getByLabelText('C3, hit')).toBeInTheDocument();
    });

    it('renders miss state for a shot cell with no ship', () => {
        const c = cell({hasShot: true, ship: null});
        render(<BoardCell cell={c} mode="target" sunk={false} isGhost={false}/>);
        expect(screen.getByLabelText('C3, miss')).toBeInTheDocument();
    });

    it('renders sunk state for a shot cell whose ship is fully sunk', () => {
        const c = cell({ship: {shipId: 's1', shipSize: 2}, hasShot: true});
        render(<BoardCell cell={c} mode="target" sunk={true} isGhost={false}/>);
        expect(screen.getByLabelText('C3, sunk')).toBeInTheDocument();
    });

    it('renders a clickable cell as a real button when onClick is provided and not readonly', () => {
        render(<BoardCell cell={cell()} mode="target" sunk={false} isGhost={false} onClick={() => {
        }}/>);
        expect(screen.getByRole('button', {name: 'C3, water'})).toBeInTheDocument();
    });

    it('renders a non-interactive div when readonly even if onClick is provided', () => {
        render(<BoardCell cell={cell()} mode="target" sunk={false} isGhost={false} onClick={() => {
        }} readonly/>);
        expect(screen.queryByRole('button')).not.toBeInTheDocument();
        expect(screen.getByLabelText('C3, water')).toBeInTheDocument();
    });

    it('renders an already-shot hit cell in target mode as a non-interactive div and does not fire onClick', async () => {
        const user = userEvent.setup();
        const onClick = vi.fn();
        const c = cell({ship: {shipId: 's1', shipSize: 2}, hasShot: true});
        render(<BoardCell cell={c} mode="target" sunk={false} isGhost={false} onClick={onClick}/>);
        const el = screen.getByLabelText('C3, hit');
        expect(el.tagName).toBe('DIV');
        await user.click(el);
        expect(onClick).not.toHaveBeenCalled();
    });

    it('renders an already-shot miss cell (no ship) in target mode as a non-interactive div and does not fire onClick', async () => {
        const user = userEvent.setup();
        const onClick = vi.fn();
        const c = cell({hasShot: true, ship: null});
        render(<BoardCell cell={c} mode="target" sunk={false} isGhost={false} onClick={onClick}/>);
        const el = screen.getByLabelText('C3, miss');
        expect(el.tagName).toBe('DIV');
        await user.click(el);
        expect(onClick).not.toHaveBeenCalled();
    });

    it('renders an already-shot sunk cell in target mode as a non-interactive div and does not fire onClick', async () => {
        const user = userEvent.setup();
        const onClick = vi.fn();
        const c = cell({ship: {shipId: 's1', shipSize: 2}, hasShot: true});
        render(<BoardCell cell={c} mode="target" sunk={true} isGhost={false} onClick={onClick}/>);
        const el = screen.getByLabelText('C3, sunk');
        expect(el.tagName).toBe('DIV');
        await user.click(el);
        expect(onClick).not.toHaveBeenCalled();
    });

    it('uses column letter and 1-based row number in the aria-label', () => {
        const c: CellDto = {row: 6, col: 5, ship: null, hasShot: false, isAvailable: true};
        render(<BoardCell cell={c} mode="own" sunk={false} isGhost={false}/>);
        // col 5 -> 'F', row 6 -> 7
        expect(screen.getByLabelText('F7, water')).toBeInTheDocument();
    });
});
