import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LoadingView } from './LoadingView';

describe('LoadingView', () => {
  it('renders the passed title and subtitle text', () => {
    render(<LoadingView title="Setting up the sea…" subtitle="Loading your game session" />);
    expect(screen.getByText('Setting up the sea…')).toBeInTheDocument();
    expect(screen.getByText('Loading your game session')).toBeInTheDocument();
  });

  it('renders a LoadingBar', () => {
    render(<LoadingView title="Title" subtitle="Subtitle" />);
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('renders the default anchor icon when none is provided', () => {
    render(<LoadingView title="Title" subtitle="Subtitle" />);
    expect(screen.getByText('⚓')).toBeInTheDocument();
  });

  it('renders a custom icon when provided', () => {
    render(<LoadingView title="Title" subtitle="Subtitle" icon={<span data-testid="custom-icon">🛰️</span>} />);
    expect(screen.getByTestId('custom-icon')).toBeInTheDocument();
    expect(screen.queryByText('⚓')).not.toBeInTheDocument();
  });
});
