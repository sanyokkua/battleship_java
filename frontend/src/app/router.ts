export type Route =
    { name: 'home' } | { name: 'create' } | { name: 'join'; gameId: string } | { name: 'game'; gameId: string };

export interface NavigationState {
    toast?: 'game-created';
}

function decodeId(value: string): string | undefined {
    try {
        return decodeURIComponent(value);
    } catch {
        return undefined;
    }
}

export function getRoute(pathname: string): Route {
    if (pathname === '/') return { name: 'home' };
    if (pathname === '/create') return { name: 'create' };
    const join = /^\/join\/([^/]+)$/.exec(pathname);
    if (join?.[1]) {
        const gameId = decodeId(join[1]);
        if (gameId !== undefined) return { name: 'join', gameId };
    }
    const game = /^\/games\/([^/]+)$/.exec(pathname);
    if (game?.[1]) {
        const gameId = decodeId(game[1]);
        if (gameId !== undefined) return { name: 'game', gameId };
    }
    return { name: 'home' };
}

export function navigate(path: string, state: NavigationState = {}): void {
    window.history.pushState(state, '', path);
    window.dispatchEvent(new PopStateEvent('popstate'));
}
