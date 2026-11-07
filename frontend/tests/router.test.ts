import { describe, expect, it } from 'vitest';
import { getRoute, navigate } from '../src/app/router';

describe('getRoute', () => {
    it('matches supported paths and sends unknown paths home', () => {
        expect(getRoute('/')).toEqual({ name: 'home' });
        expect(getRoute('/create')).toEqual({ name: 'create' });
        expect(getRoute('/join/abc-123')).toEqual({ name: 'join', gameId: 'abc-123' });
        expect(getRoute('/games/abc-123')).toEqual({ name: 'game', gameId: 'abc-123' });
        expect(getRoute('/unknown')).toEqual({ name: 'home' });
        expect(getRoute('/join/%')).toEqual({ name: 'home' });
        expect(getRoute('/games/%E0%A4%A')).toEqual({ name: 'home' });
    });

    it('notifies route subscribers after navigation and browser history changes', () => {
        const onRouteChange = vi.fn();
        window.addEventListener('popstate', onRouteChange);
        navigate('/create');
        expect(window.location.pathname).toBe('/create');
        expect(onRouteChange).toHaveBeenCalledOnce();
        window.history.pushState({}, '', '/games/abc-123');
        window.dispatchEvent(new PopStateEvent('popstate'));
        expect(getRoute(window.location.pathname)).toEqual({ name: 'game', gameId: 'abc-123' });
        window.removeEventListener('popstate', onRouteChange);
    });
});
