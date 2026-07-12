import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Input } from './Input';

describe('Input', () => {
  it('renders without the error class by default', () => {
    render(<Input placeholder="name" />);
    const input = screen.getByPlaceholderText('name');
    expect(input).toHaveClass('input');
    expect(input).not.toHaveClass('err');
  });

  it('applies the error class when error=true', () => {
    render(<Input placeholder="name" error />);
    expect(screen.getByPlaceholderText('name')).toHaveClass('err');
  });

  it('forwards native input props', () => {
    render(<Input placeholder="name" value="Batman" readOnly />);
    expect(screen.getByPlaceholderText('name')).toHaveValue('Batman');
  });
});
