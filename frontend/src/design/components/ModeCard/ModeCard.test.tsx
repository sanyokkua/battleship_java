import {describe, expect, it, vi} from 'vitest';
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {ModeCard} from './ModeCard';

function renderCard(overrides: Partial<Parameters<typeof ModeCard>[0]> = {}) {
    const onSelect = vi.fn();
    render(
        <ModeCard
            icon="🇺🇦"
            name="Ukrainian"
            description="10 ships · sizes 1–4 · 20 cells"
            shipSizes={[4, 3, 3, 2, 1]}
            selected={false}
            onSelect={onSelect}
            {...overrides}
        />,
    );
    return {onSelect};
}

describe('ModeCard', () => {
    it('renders the name and description', () => {
        renderCard();
        expect(screen.getByRole('radio', {name: /Ukrainian/})).toBeInTheDocument();
        expect(screen.getByText('10 ships · sizes 1–4 · 20 cells')).toBeInTheDocument();
    });

    it('renders one chip per ship size', () => {
        const {container} = render(
            <ModeCard
                icon="🇺🇦"
                name="Ukrainian"
                description="desc"
                shipSizes={[4, 3, 3, 2, 1]}
                selected={false}
                onSelect={() => {
                }}
            />,
        );
        expect(container.querySelectorAll('.mc-ships i')).toHaveLength(5);
    });

    it('reflects selected via aria-checked and aria-pressed', () => {
        renderCard({selected: true});
        const btn = screen.getByRole('radio', {name: /Ukrainian/});
        expect(btn).toHaveAttribute('aria-checked', 'true');
        expect(btn).toHaveAttribute('aria-pressed', 'true');
    });

    it('reflects unselected via aria-checked and aria-pressed', () => {
        renderCard({selected: false});
        const btn = screen.getByRole('radio', {name: /Ukrainian/});
        expect(btn).toHaveAttribute('aria-checked', 'false');
        expect(btn).toHaveAttribute('aria-pressed', 'false');
    });

    it('fires onSelect on click', async () => {
        const user = userEvent.setup();
        const {onSelect} = renderCard();
        await user.click(screen.getByRole('radio', {name: /Ukrainian/}));
        expect(onSelect).toHaveBeenCalledTimes(1);
    });

    it('fires onSelect on Enter key press', async () => {
        const user = userEvent.setup();
        const {onSelect} = renderCard();
        const btn = screen.getByRole('radio', {name: /Ukrainian/});
        btn.focus();
        await user.keyboard('{Enter}');
        expect(onSelect).toHaveBeenCalledTimes(1);
    });

    it('fires onSelect on Space key press', async () => {
        const user = userEvent.setup();
        const {onSelect} = renderCard();
        const btn = screen.getByRole('radio', {name: /Ukrainian/});
        btn.focus();
        await user.keyboard(' ');
        expect(onSelect).toHaveBeenCalledTimes(1);
    });
});
