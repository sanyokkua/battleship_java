import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PlayerCard, type PlayerCardProps } from './PlayerCard';

function baseProps(overrides: Partial<PlayerCardProps> = {}): PlayerCardProps {
  return {
    variant: 'you',
    name: 'Alice',
    cellsLabel: 'Cells',
    cellsValue: 12,
    cellsPercent: 60,
    shipsLabel: 'Ships',
    shipsAliveCount: 5,
    shipsTotal: 10,
    ...overrides,
  };
}

function miniBarFills(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll('.mini-bar i'));
}

describe('PlayerCard — health color tiering', () => {
  it('uses the green/"ok" color at exactly the 60% boundary', () => {
    const { container } = render(<PlayerCard {...baseProps({ cellsPercent: 60 })} />);
    const [cellsFill] = miniBarFills(container);
    expect(cellsFill.style.background).toBe('var(--ok)');
  });

  it('uses the green/"ok" color above 60%', () => {
    const { container } = render(<PlayerCard {...baseProps({ cellsPercent: 85 })} />);
    const [cellsFill] = miniBarFills(container);
    expect(cellsFill.style.background).toBe('var(--ok)');
  });

  it('uses the gold color just below the 60% boundary (59%)', () => {
    const { container } = render(<PlayerCard {...baseProps({ cellsPercent: 59 })} />);
    const [cellsFill] = miniBarFills(container);
    expect(cellsFill.style.background).toBe('var(--gold)');
  });

  it('uses the gold color at exactly the 30% boundary', () => {
    const { container } = render(<PlayerCard {...baseProps({ cellsPercent: 30 })} />);
    const [cellsFill] = miniBarFills(container);
    expect(cellsFill.style.background).toBe('var(--gold)');
  });

  it('uses the red/"hit" color just below the 30% boundary (29%)', () => {
    const { container } = render(<PlayerCard {...baseProps({ cellsPercent: 29 })} />);
    const [cellsFill] = miniBarFills(container);
    expect(cellsFill.style.background).toBe('var(--hit)');
  });

  it('uses the red/"hit" color at 0%', () => {
    const { container } = render(<PlayerCard {...baseProps({ cellsPercent: 0 })} />);
    const [cellsFill] = miniBarFills(container);
    expect(cellsFill.style.background).toBe('var(--hit)');
  });

  it('applies the same tiered color to both the cells and ships mini-bars', () => {
    const { container } = render(<PlayerCard {...baseProps({ cellsPercent: 85, shipsAliveCount: 8, shipsTotal: 10 })} />);
    const [cellsFill, shipsFill] = miniBarFills(container);
    expect(cellsFill.style.background).toBe('var(--ok)');
    expect(shipsFill.style.background).toBe('var(--ok)');
  });
});

describe('PlayerCard — percentage clamping', () => {
  it('clamps a cellsPercent above 100 down to 100 for the bar width', () => {
    const { container } = render(<PlayerCard {...baseProps({ cellsPercent: 150 })} />);
    const [cellsFill] = miniBarFills(container);
    expect(cellsFill.style.width).toBe('100%');
  });

  it('clamps a negative cellsPercent up to 0 for the bar width', () => {
    const { container } = render(<PlayerCard {...baseProps({ cellsPercent: -20 })} />);
    const [cellsFill] = miniBarFills(container);
    expect(cellsFill.style.width).toBe('0%');
  });

  it('renders an in-range cellsPercent as-is', () => {
    const { container } = render(<PlayerCard {...baseProps({ cellsPercent: 42 })} />);
    const [cellsFill] = miniBarFills(container);
    expect(cellsFill.style.width).toBe('42%');
  });

  it('derives the ships mini-bar width from shipsAliveCount/shipsTotal and clamps it too', () => {
    const { container } = render(<PlayerCard {...baseProps({ shipsAliveCount: 3, shipsTotal: 10 })} />);
    const [, shipsFill] = miniBarFills(container);
    expect(shipsFill.style.width).toBe('30%');
  });
});

describe('PlayerCard — zero-ships-total safe-division guard', () => {
  it('renders a 0% ships bar width instead of NaN/Infinity when shipsTotal is 0', () => {
    const { container } = render(<PlayerCard {...baseProps({ shipsAliveCount: 0, shipsTotal: 0 })} />);
    const [, shipsFill] = miniBarFills(container);
    expect(shipsFill.style.width).toBe('0%');
    expect(shipsFill.style.width).not.toBe('NaN%');
  });

  it('still renders the ships count text as "0 / 0" without throwing', () => {
    render(<PlayerCard {...baseProps({ shipsAliveCount: 0, shipsTotal: 0 })} />);
    expect(screen.getByText('0 / 0')).toBeInTheDocument();
  });
});

describe('PlayerCard — "you" vs "foe" variant rendering', () => {
  it('renders the "you" variant class and an optional youLabel badge', () => {
    const { container } = render(<PlayerCard {...baseProps({ variant: 'you', youLabel: 'You' })} />);
    expect(container.querySelector('.player-card')).toHaveClass('you');
    expect(screen.getByText('You')).toBeInTheDocument();
  });

  it('does not render a youLabel badge for the "you" variant when youLabel is omitted', () => {
    render(<PlayerCard {...baseProps({ variant: 'you', youLabel: undefined })} />);
    expect(screen.queryByText('You')).not.toBeInTheDocument();
  });

  it('renders the "foe" variant class and never shows the youLabel badge even if provided', () => {
    const { container } = render(<PlayerCard {...baseProps({ variant: 'foe', youLabel: 'You' })} />);
    expect(container.querySelector('.player-card')).toHaveClass('foe');
    expect(screen.queryByText('You')).not.toBeInTheDocument();
  });

  it('shows the turn-indicator pill on the foe card when isActiveTurn is true', () => {
    render(<PlayerCard {...baseProps({ variant: 'foe', isActiveTurn: true })} />);
    expect(screen.getByText('▶')).toBeInTheDocument();
  });

  it('hides the turn-indicator pill on the foe card when isActiveTurn is false/undefined', () => {
    render(<PlayerCard {...baseProps({ variant: 'foe', isActiveTurn: false })} />);
    expect(screen.queryByText('▶')).not.toBeInTheDocument();
  });

  it('never shows the turn-indicator pill on the "you" card even if isActiveTurn is true', () => {
    render(<PlayerCard {...baseProps({ variant: 'you', isActiveTurn: true })} />);
    expect(screen.queryByText('▶')).not.toBeInTheDocument();
  });

  it('renders the player name for both variants', () => {
    render(<PlayerCard {...baseProps({ variant: 'you', name: 'Alice' })} />);
    expect(screen.getByText('Alice')).toBeInTheDocument();
  });
});
