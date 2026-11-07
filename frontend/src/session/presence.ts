import { sendPresence } from '../gateway/gameGateway';
import { getGameStore, type GameId } from './gameStore';

export function startPresence(gameId: GameId, intervalSeconds: number): () => void {
    let timer: ReturnType<typeof globalThis.setTimeout> | undefined;
    let stopped = false;
    const store = getGameStore(gameId);
    const canSend = () => {
        const state = store.getSnapshot();
        return (
            !stopped &&
            document.visibilityState === 'visible' &&
            state.connection === 'connected' &&
            state.snapshot?.allowedActions.includes('SEND_PRESENCE') === true
        );
    };
    // Arms one timer while presence is allowed; store publishes must not restart a pending timer.
    const schedule = () => {
        if (!canSend()) {
            globalThis.clearTimeout(timer);
            timer = undefined;
            return;
        }
        if (timer !== undefined) return;
        timer = globalThis.setTimeout(() => {
            timer = undefined;
            if (canSend()) void sendPresence(gameId).catch(() => undefined);
            schedule();
        }, intervalSeconds * 1000);
    };
    document.addEventListener('visibilitychange', schedule);
    const unsubscribe = store.subscribe(schedule);
    schedule();
    return () => {
        stopped = true;
        globalThis.clearTimeout(timer);
        document.removeEventListener('visibilitychange', schedule);
        unsubscribe();
    };
}
