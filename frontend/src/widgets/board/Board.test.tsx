import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { CellDto } from '../../logic/ApplicationTypes';
import { Board } from './Board';

function emptyField(): CellDto[][] {
  const field: CellDto[][] = [];
  for (let row = 0; row < 10; row++) {
    const r: CellDto[] = [];
    for (let col = 0; col < 10; col++) {
      r.push({ row, col, ship: null, hasShot: false, isAvailable: true });
    }
    field.push(r);
  }
  return field;
}

describe('Board', () => {
  it('renders a 10x10 grid of 100 cells', () => {
    render(<Board field={emptyField()} mode="own" />);
    // Every cell (water, no onClick) renders as a div with an aria-label.
    const cells = screen.getAllByLabelText(/, water$/);
    expect(cells).toHaveLength(100);
  });

  it('marks cells of a partially-hit ship as hit, not sunk', () => {
    const field = emptyField();
    // 3-cell horizontal ship at row 0, cols 0-2; only cols 0 and 1 are hit.
    const shipId = 'ship-a';
    field[0][0] = { row: 0, col: 0, ship: { shipId, shipSize: 3 }, hasShot: true, isAvailable: false };
    field[0][1] = { row: 0, col: 1, ship: { shipId, shipSize: 3 }, hasShot: true, isAvailable: false };
    field[0][2] = { row: 0, col: 2, ship: { shipId, shipSize: 3 }, hasShot: false, isAvailable: false };

    render(<Board field={field} mode="target" />);

    expect(screen.getByLabelText('A1, hit')).toBeInTheDocument();
    expect(screen.getByLabelText('B1, hit')).toBeInTheDocument();
    expect(screen.queryByLabelText('A1, sunk')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('B1, sunk')).not.toBeInTheDocument();
  });

  it('marks all cells of a fully-hit ship as sunk', () => {
    const field = emptyField();
    // 2-cell ship, both cells hit.
    const shipId = 'ship-b';
    field[5][5] = { row: 5, col: 5, ship: { shipId, shipSize: 2 }, hasShot: true, isAvailable: false };
    field[5][6] = { row: 5, col: 6, ship: { shipId, shipSize: 2 }, hasShot: true, isAvailable: false };

    render(<Board field={field} mode="target" />);

    expect(screen.getByLabelText('F6, sunk')).toBeInTheDocument();
    expect(screen.getByLabelText('G6, sunk')).toBeInTheDocument();
  });

  it('fires onCellClick with (row, col) on click', async () => {
    const user = userEvent.setup();
    const onCellClick = vi.fn();
    render(<Board field={emptyField()} mode="target" onCellClick={onCellClick} />);

    await user.click(screen.getByLabelText('A1, water'));
    expect(onCellClick).toHaveBeenCalledWith(0, 0);

    await user.click(screen.getByLabelText('J10, water'));
    expect(onCellClick).toHaveBeenCalledWith(9, 9);
  });

  it('fires onCellClick via keyboard activation (Enter/Space) since clickable cells are buttons', async () => {
    const user = userEvent.setup();
    const onCellClick = vi.fn();
    render(<Board field={emptyField()} mode="target" onCellClick={onCellClick} />);

    const button = screen.getByLabelText('C3, water');
    button.focus();
    await user.keyboard('{Enter}');
    expect(onCellClick).toHaveBeenCalledWith(2, 2);

    onCellClick.mockClear();
    button.focus();
    await user.keyboard(' ');
    expect(onCellClick).toHaveBeenCalledWith(2, 2);
  });

  it('does not fire onCellClick for readonly boards', async () => {
    const user = userEvent.setup();
    const onCellClick = vi.fn();
    render(<Board field={emptyField()} mode="result-target" onCellClick={onCellClick} readonly />);

    await user.click(screen.getByLabelText('A1, water'));
    expect(onCellClick).not.toHaveBeenCalled();
  });
});
