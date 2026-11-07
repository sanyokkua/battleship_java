import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import { ErrorBoundary } from '../src/app/ErrorBoundary';

function Broken(): never {
    throw new Error('render failure');
}

afterEach(() => {
    vi.restoreAllMocks();
});

it('replaces a crashed tree with a recovery screen offering refresh and home', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const onHome = vi.fn();
    const onRefresh = vi.fn();
    render(
        <ErrorBoundary onHome={onHome} onRefresh={onRefresh}>
            <Broken />
        </ErrorBoundary>,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Refresh' }));
    await userEvent.click(screen.getByRole('button', { name: 'Return home' }));
    expect(onRefresh).toHaveBeenCalledOnce();
    expect(onHome).toHaveBeenCalledOnce();
});

it('renders its children when nothing fails', () => {
    render(
        <ErrorBoundary>
            <p>fine</p>
        </ErrorBoundary>,
    );
    expect(screen.getByText('fine')).toBeInTheDocument();
});
