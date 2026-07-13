import {describe, expect, it, vi} from 'vitest';
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {BoardTabs} from './BoardTabs';

describe('BoardTabs', () => {
    it('fires onChange with "fleet" when the fleet tab is clicked', async () => {
        const user = userEvent.setup();
        const onChange = vi.fn();
        render(<BoardTabs active="target" onChange={onChange} targetLabel="Target grid" fleetLabel="Your fleet"/>);

        await user.click(screen.getByRole('tab', {name: 'Your fleet'}));
        expect(onChange).toHaveBeenCalledWith('fleet');
    });

    it('fires onChange with "target" when the target tab is clicked', async () => {
        const user = userEvent.setup();
        const onChange = vi.fn();
        render(<BoardTabs active="fleet" onChange={onChange} targetLabel="Target grid" fleetLabel="Your fleet"/>);

        await user.click(screen.getByRole('tab', {name: 'Target grid'}));
        expect(onChange).toHaveBeenCalledWith('target');
    });

    it('marks the active tab with aria-selected=true and the other false', () => {
        render(<BoardTabs active="target" onChange={() => {
        }} targetLabel="Target grid" fleetLabel="Your fleet"/>);

        expect(screen.getByRole('tab', {name: 'Target grid'})).toHaveAttribute('aria-selected', 'true');
        expect(screen.getByRole('tab', {name: 'Your fleet'})).toHaveAttribute('aria-selected', 'false');
    });
});
