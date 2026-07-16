import {describe, expect, it} from 'vitest';
import {render, screen} from '@testing-library/react';
import {LoadingBar} from './LoadingBar';

describe('LoadingBar', () => {
    it('renders without crashing', () => {
        render(<LoadingBar/>);
        expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });

    it('renders the inner animated bar element', () => {
        const {container} = render(<LoadingBar/>);
        expect(container.querySelector('.topbar-progress > i')).toBeInTheDocument();
    });

    it('forwards an accessible label when provided', () => {
        render(<LoadingBar label="Loading your game session"/>);
        expect(screen.getByRole('progressbar', {name: 'Loading your game session'})).toBeInTheDocument();
    });
});
