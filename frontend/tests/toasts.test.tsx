import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { Toasts } from '../src/components/Toasts';

afterEach(() => vi.useRealTimers());

it('announces a success politely, closes it immediately, and auto-dismisses after 6.5 seconds', () => {
    vi.useFakeTimers();
    const onDismiss = vi.fn();
    const { rerender } = render(
        <Toasts toast={{ id: 1, kind: 'success', message: 'Game created' }} onDismiss={onDismiss} />,
    );

    expect(screen.getByRole('region', { name: 'Notifications' })).toHaveAttribute('aria-live', 'polite');
    expect(screen.getByRole('status')).toHaveTextContent('Game created');
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss notification' }));
    expect(onDismiss).toHaveBeenCalledOnce();

    rerender(<Toasts toast={{ id: 2, kind: 'error', message: 'Could not copy link' }} onDismiss={onDismiss} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Could not copy link');
    act(() => {
        vi.advanceTimersByTime(6500);
    });
    expect(onDismiss).toHaveBeenCalledTimes(2);
});
