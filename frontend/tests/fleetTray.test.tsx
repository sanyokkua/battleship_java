import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { FleetTray } from '../src/components/FleetTray';
import type { components } from '../src/contract/generated/api';

const ships: components['schemas']['Ship'][] = [
    { shipId: 's1', shipTypeId: 'ship-2', length: 2, cells: [], status: 'INTACT' },
];
afterEach(cleanup);
it('does not initiate a drag when touching the inventory', () => {
    const start = vi.fn();
    render(<FleetTray ships={ships} disabled={false} onSelect={vi.fn()} onShipPointerDown={start} />);
    fireEvent.pointerDown(screen.getByRole('button'), { pointerType: 'touch', isPrimary: true, button: 0 });
    expect(start).not.toHaveBeenCalled();
});
