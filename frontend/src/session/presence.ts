import { sendPresence } from '../gateway/gameGateway';
import { getGameStore, type GameId } from './gameStore';

export function startPresence(gameId: GameId, intervalSeconds: number): () => void {
    let timer = 0;
    let stopped = false;
    const store = getGameStore(gameId);
    const schedule = () => {
        window.clearTimeout(timer);
        if (stopped || document.visibilityState !== 'visible') return;
        const state = store.getSnapshot();
        if (state.connection !== 'connected' || !state.snapshot?.allowedActions.includes('SEND_PRESENCE')) return;
        timer = window.setTimeout(() => {
            if (
                document.visibilityState === 'visible' &&
                store.getSnapshot().snapshot?.allowedActions.includes('SEND_PRESENCE')
            ) {
                void sendPresence(gameId).catch(() => undefined);
            }
            schedule();
        }, intervalSeconds * 1000);
    };
    const onVisibility = () => {
        schedule();
    };
    document.addEventListener('visibilitychange', onVisibility);
    const unsubscribe = store.subscribe(schedule);
    schedule();
    return () => {
        stopped = true;
        window.clearTimeout(timer);
        document.removeEventListener('visibilitychange', onVisibility);
        unsubscribe();
    };
}
