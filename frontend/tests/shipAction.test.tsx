import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { ShipActionDialog } from '../src/screens/ShipActionDialog';
import type { components } from '../src/contract/generated/api';
const ship: components['schemas']['Ship'] = {
    shipId: 's1',
    shipTypeId: 'ship-2',
    length: 2,
    cells: [],
    status: 'INTACT',
};
afterEach(cleanup);
it('offers only placements that fit from the chosen edge cell', () => {
    HTMLDialogElement.prototype.showModal = function () {
        this.open = true;
    };
    HTMLDialogElement.prototype.close = vi.fn();
    render(
        <ShipActionDialog
            open
            anchor={{ rowIndex: 0, columnIndex: 9 }}
            ships={[ship]}
            selectedShip={undefined}
            editing={false}
            allowed={['PLACE_SHIP']}
            onClose={vi.fn()}
            onPickAnchor={vi.fn()}
            onCommand={vi.fn()}
            dimensions={{ rows: 10, columns: 10 }}
            shipsMayTouch={false}
        />,
    );
    expect(screen.getByRole('dialog').tagName).toBe('DIALOG');
    expect(screen.queryByRole('button', { name: /horizontal/i })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /vertical/i })).toBeInTheDocument();
});
it('excludes the moving ship by identity while respecting other ships and their contact halo', () => {
    const moving: components['schemas']['Ship'] = {
        ...ship,
        anchor: { rowIndex: 0, columnIndex: 0 },
        orientation: 'HORIZONTAL',
        cells: [
            { rowIndex: 0, columnIndex: 0 },
            { rowIndex: 0, columnIndex: 1 },
        ],
    };
    const other: components['schemas']['Ship'] = {
        ...ship,
        shipId: 's2',
        anchor: { rowIndex: 0, columnIndex: 3 },
        orientation: 'HORIZONTAL',
        cells: [
            { rowIndex: 0, columnIndex: 3 },
            { rowIndex: 0, columnIndex: 4 },
        ],
    };
    const props = {
        open: true,
        ships: [moving, other],
        selectedShip: moving,
        editing: false,
        allowed: ['PLACE_SHIP'] as components['schemas']['Action'][],
        onClose: vi.fn(),
        onPickAnchor: vi.fn(),
        onCommand: vi.fn(),
        dimensions: { rows: 10, columns: 10 },
        shipsMayTouch: false,
    };
    const { rerender } = render(<ShipActionDialog {...props} anchor={{ rowIndex: 0, columnIndex: 0 }} />);
    expect(screen.getByRole('button', { name: /horizontal/i })).toBeInTheDocument();
    rerender(<ShipActionDialog {...props} anchor={{ rowIndex: 1, columnIndex: 1 }} />);
    expect(screen.queryByRole('button', { name: /horizontal/i })).not.toBeInTheDocument();
    rerender(<ShipActionDialog {...props} shipsMayTouch anchor={{ rowIndex: 1, columnIndex: 1 }} />);
    expect(screen.getByRole('button', { name: /horizontal/i })).toBeInTheDocument();
    rerender(<ShipActionDialog {...props} shipsMayTouch anchor={{ rowIndex: 0, columnIndex: 3 }} />);
    expect(screen.queryByRole('button', { name: /horizontal/i })).not.toBeInTheDocument();
});
it('offers rotation only when the other orientation is legal', () => {
    const moving: components['schemas']['Ship'] = {
        ...ship,
        anchor: { rowIndex: 9, columnIndex: 0 },
        orientation: 'HORIZONTAL',
        cells: [
            { rowIndex: 9, columnIndex: 0 },
            { rowIndex: 9, columnIndex: 1 },
        ],
    };
    render(
        <ShipActionDialog
            open
            anchor={{ rowIndex: 9, columnIndex: 0 }}
            ships={[moving]}
            selectedShip={moving}
            editing
            allowed={['PLACE_SHIP', 'REMOVE_SHIP']}
            onClose={vi.fn()}
            onPickAnchor={vi.fn()}
            onCommand={vi.fn()}
            dimensions={{ rows: 10, columns: 10 }}
            shipsMayTouch={false}
        />,
    );
    expect(screen.queryByRole('button', { name: 'Rotate' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Move' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Remove' })).toBeInTheDocument();
});
it('keeps the chosen popup control focused across unrelated rerenders', () => {
    const props = {
        open: true,
        anchor: { rowIndex: 0, columnIndex: 0 },
        ships: [ship],
        selectedShip: undefined,
        editing: false,
        allowed: ['PLACE_SHIP'] as components['schemas']['Action'][],
        onClose: vi.fn(),
        onPickAnchor: vi.fn(),
        onCommand: vi.fn(),
        dimensions: { rows: 10, columns: 10 },
        shipsMayTouch: false,
    };
    const { rerender } = render(<ShipActionDialog {...props} />);
    const vertical = screen.getByRole('button', { name: /vertical/i });
    vertical.focus();
    rerender(<ShipActionDialog {...props} />);
    expect(vertical).toHaveFocus();
});
