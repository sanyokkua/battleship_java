import { render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { Qr } from '../src/components/Qr';

vi.mock('qrcode', () => ({ toCanvas: () => Promise.reject(new Error('canvas unsupported')) }));

// An undrawable code must say so instead of leaving a silent blank canvas and an unhandled rejection.
it('shows fallback text when the QR code cannot be drawn', async () => {
    render(<Qr value="https://example.test/join/game#invite=secret" label="QR code for invitation" />);

    expect(await screen.findByRole('alert')).toHaveTextContent('QR code could not be drawn');
});
