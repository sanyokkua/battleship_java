import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { Qr } from '../src/components/Qr';

const toCanvas = vi.hoisted(() => vi.fn(() => Promise.resolve()));
vi.mock('qrcode', () => ({ toCanvas }));

beforeEach(() => toCanvas.mockClear());

it('renders a labelled canvas and encodes the supplied invitation URL', async () => {
    render(<Qr value="https://example.test/join/game#invite=secret" label="QR code for invitation" />);

    const canvas = screen.getByLabelText('QR code for invitation');
    expect(canvas.tagName).toBe('CANVAS');
    await waitFor(() => {
        expect(toCanvas).toHaveBeenCalledWith(
            canvas,
            'https://example.test/join/game#invite=secret',
            expect.any(Object),
        );
    });
});
