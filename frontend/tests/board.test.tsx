import { act, cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { Board } from '../src/components/Board';
import { preferenceStore } from '../src/preferences/preferences';
import type { components } from '../src/contract/generated/api';

const board: components['schemas']['Board'] = {
    grid: [['UNKNOWN', 'WATER', 'SHIP', 'MISS', 'HIT', 'SUNK', 'REVEALED_WATER']],
    ships: [],
};
const props = { board, dimensions: { rows: 1, columns: 7 }, label: 'Sea' };
let reducedMotion = false;
let motion: EventTarget;

beforeEach(() => {
    vi.useFakeTimers();
    reducedMotion = false;
    motion = new EventTarget();
    Object.defineProperty(motion, 'matches', { get: () => reducedMotion });
    vi.stubGlobal('matchMedia', () => motion);
    preferenceStore.update({ emoji: false, locale: 'en' });
});
afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
});

// Restoring a static ship/water/unknown glyph must fail this observable content assertion.
it('keeps ordinary cells free of visible glyphs while preserving coordinate and state names', () => {
    render(<Board {...props} />);
    for (const name of ['A1, unknown', 'B1, water', 'C1, ship', 'D1, miss', 'G1, revealed water']) {
        const cell = screen.getByRole('gridcell', { name });
        expect(cell.querySelector('[aria-hidden="true"]')?.textContent ?? '').toBe('');
    }
    expect(screen.getByRole('gridcell', { name: 'E1, hit' }).querySelector('.cell-mark')).toHaveTextContent('×');
    expect(screen.getByRole('gridcell', { name: 'F1, sunk' }).querySelector('.cell-mark')).toHaveTextContent('✕');
});

it('shows only temporary preference-gated firing and new miss feedback', () => {
    const { container, rerender } = render(<Board {...props} />);
    rerender(<Board {...props} pendingTarget={{ rowIndex: 0, columnIndex: 0 }} />);
    expect(container.querySelector('.cell-emoji')).toBeNull();
    act(() => {
        preferenceStore.update({ emoji: true });
    });
    rerender(<Board {...props} />);
    rerender(<Board {...props} pendingTarget={{ rowIndex: 0, columnIndex: 1 }} />);
    expect(container.querySelector('.cell-emoji')).toHaveTextContent('🚀');
    act(() => {
        vi.advanceTimersByTime(900);
    });
    expect(container.querySelector('.cell-emoji')).toBeNull();
    rerender(<Board {...props} shot={{ by: 'YOU', target: { rowIndex: 0, columnIndex: 3 }, result: 'MISS' }} />);
    expect(container.querySelector('.cell-emoji')).toHaveTextContent('💦');
    act(() => {
        vi.advanceTimersByTime(900);
    });
    expect(container.querySelector('.cell-emoji')).toBeNull();
    rerender(<Board {...props} shot={{ by: 'YOU', target: { rowIndex: 0, columnIndex: 4 }, result: 'HIT' }} />);
    expect(container.querySelector('.cell-emoji')).toBeNull();
});

it('suppresses decorative feedback for reduced motion and does not animate a loaded shot', () => {
    reducedMotion = true;
    preferenceStore.update({ emoji: true });
    const { container, rerender } = render(
        <Board {...props} shot={{ by: 'YOU', target: { rowIndex: 0, columnIndex: 3 }, result: 'MISS' }} />,
    );
    rerender(<Board {...props} pendingTarget={{ rowIndex: 0, columnIndex: 0 }} />);
    expect(container.querySelector('.cell-emoji')).toBeNull();
});

// Toggling a preference or receiving the same snapshot must not replay an old shot.
it('does not replay feedback after preference toggles, same-shot renders, or motion changes', () => {
    preferenceStore.update({ emoji: true });
    const shot = { by: 'YOU', target: { rowIndex: 0, columnIndex: 3 }, result: 'MISS' } as const;
    const { container, rerender } = render(<Board {...props} shot={shot} />);
    expect(container.querySelector('.cell-emoji')).toBeNull();
    rerender(<Board {...props} shot={{ ...shot, target: { rowIndex: 0, columnIndex: 2 } }} />);
    expect(container.querySelector('.cell-emoji')).toHaveTextContent('💦');
    act(() => {
        reducedMotion = true;
        motion.dispatchEvent(new Event('change'));
    });
    expect(container.querySelector('.cell-emoji')).toBeNull();
    act(() => {
        reducedMotion = false;
        motion.dispatchEvent(new Event('change'));
    });
    expect(container.querySelector('.cell-emoji')).toBeNull();
    act(() => {
        preferenceStore.update({ emoji: false });
    });
    act(() => {
        preferenceStore.update({ emoji: true });
    });
    expect(container.querySelector('.cell-emoji')).toBeNull();
    rerender(<Board {...props} shot={{ ...shot, target: { rowIndex: 0, columnIndex: 2 } }} />);
    expect(container.querySelector('.cell-emoji')).toBeNull();
});

it('keeps miss feedback visible after the accepted pending command settles', () => {
    preferenceStore.update({ emoji: true });
    const { container, rerender } = render(<Board {...props} />);
    const target = { rowIndex: 0, columnIndex: 3 };
    const shot = { by: 'YOU', target, result: 'MISS' } as const;
    rerender(<Board {...props} pendingTarget={target} />);
    rerender(<Board {...props} pendingTarget={target} shot={shot} />);
    expect(container.querySelector('.cell-emoji')).toHaveTextContent('💦');
    rerender(<Board {...props} shot={shot} />);
    expect(container.querySelector('.cell-emoji')).toHaveTextContent('💦');
});
