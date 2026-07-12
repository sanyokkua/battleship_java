import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Legend } from './Legend';

const labels = {
  water: 'Water',
  ship: 'Your ship',
  hit: 'Hit',
  miss: 'Miss',
  sunk: 'Sunk',
  noGo: 'No-go zone',
};

describe('Legend', () => {
  it('renders 5 chips when withNoGo is false', () => {
    const { container } = render(<Legend withNoGo={false} labels={labels} />);
    const chips = container.querySelectorAll('.legend > span');
    expect(chips).toHaveLength(5);
    expect(screen.queryByText('No-go zone')).not.toBeInTheDocument();
  });

  it('renders 6 chips when withNoGo is true', () => {
    const { container } = render(<Legend withNoGo={true} labels={labels} />);
    const chips = container.querySelectorAll('.legend > span');
    expect(chips).toHaveLength(6);
    expect(screen.getByText('No-go zone')).toBeInTheDocument();
  });

  it('renders the passed-in label text, not hard-coded English', () => {
    const customLabels = {
      water: 'Вода',
      ship: 'Ваш корабель',
      hit: 'Влучання',
      miss: 'Промах',
      sunk: 'Потоплено',
      noGo: 'Заборонена зона',
    };
    render(<Legend withNoGo={true} labels={customLabels} />);
    expect(screen.getByText('Вода')).toBeInTheDocument();
    expect(screen.getByText('Ваш корабель')).toBeInTheDocument();
    expect(screen.getByText('Влучання')).toBeInTheDocument();
    expect(screen.getByText('Промах')).toBeInTheDocument();
    expect(screen.getByText('Потоплено')).toBeInTheDocument();
    expect(screen.getByText('Заборонена зона')).toBeInTheDocument();
  });
});
