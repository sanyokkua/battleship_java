import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Pill } from './Pill';

describe('Pill', () => {
  it.each(['warn', 'ok', 'turn'] as const)('renders the %s variant class', (variant) => {
    render(<Pill variant={variant}>{variant}</Pill>);
    expect(screen.getByText(variant)).toHaveClass('pill', variant);
  });
});
