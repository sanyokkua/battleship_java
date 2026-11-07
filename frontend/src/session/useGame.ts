import { useSyncExternalStore } from 'react';
import { getGameStore, type GameId } from './gameStore';

export function useGame(gameId: GameId) {
    const store = getGameStore(gameId);
    return useSyncExternalStore(
        (listener) => store.subscribe(listener),
        () => store.getSnapshot(),
        () => store.getSnapshot(),
    );
}
