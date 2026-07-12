import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { ShipTray } from './ShipTray';
import type { ShipItemData } from './ShipItem';

const fleet: ShipItemData[] = [
  { shipId: 'size3-unplaced', shipSize: 3, typeName: 'Destroyer', placed: false, active: false },
  { shipId: 'size2-unplaced', shipSize: 2, typeName: 'Submarine', placed: false, active: false },
  { shipId: 'size3-placed', shipSize: 3, typeName: 'Destroyer', placed: true, active: false },
  { shipId: 'size2-placed', shipSize: 2, typeName: 'Submarine', placed: true, active: false },
];

describe('ShipTray', () => {
  it('sorts placed ships before unplaced ships within the same size group, size groups descending', () => {
    render(
      <ShipTray
        ships={fleet}
        activeShipId={null}
        onSelectShip={() => {}}
        onRemoveShip={() => {}}
        fleetLabel="Fleet"
        hint="Tap a ship, then a cell"
        cellSingularLabel="cell"
        cellPluralLabel="cells"
        removeLabel={(typeName) => `Placed: ${typeName} ✕`}
      />,
    );

    const items = document.querySelectorAll('.fleet-panel .ship-item');
    expect(items).toHaveLength(4);

    // Group order: all size-3 entries before all size-2 entries.
    const sizeOf = (el: Element) => (el.textContent?.includes('3 cells') ? 3 : el.textContent?.includes('2 cells') ? 2 : 0);
    expect(sizeOf(items[0])).toBe(3);
    expect(sizeOf(items[1])).toBe(3);
    expect(sizeOf(items[2])).toBe(2);
    expect(sizeOf(items[3])).toBe(2);

    // Within each size group, the placed entry (rendered as a non-button <div>)
    // comes before the unplaced entry (rendered as a <button>).
    expect(items[0].tagName).toBe('DIV');
    expect(items[0].classList.contains('placed')).toBe(true);
    expect(items[1].tagName).toBe('BUTTON');
    expect(items[1].classList.contains('placed')).toBe(false);

    expect(items[2].tagName).toBe('DIV');
    expect(items[2].classList.contains('placed')).toBe(true);
    expect(items[3].tagName).toBe('BUTTON');
    expect(items[3].classList.contains('placed')).toBe(false);
  });
});
