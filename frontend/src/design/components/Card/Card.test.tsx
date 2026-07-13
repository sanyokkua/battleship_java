import {describe, expect, it} from 'vitest';
import {render, screen} from '@testing-library/react';
import {Card} from './Card';

describe('Card', () => {
    it('renders children inside a .card container', () => {
        render(
            <Card>
                <p>Contents</p>
            </Card>,
        );
        const content = screen.getByText('Contents');
        expect(content.parentElement).toHaveClass('card');
    });

    it('merges an additional className', () => {
        render(<Card className="stack">child</Card>);
        expect(screen.getByText('child')).toHaveClass('card', 'stack');
    });
});
