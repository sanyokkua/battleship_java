import { getGameStore, type GameId } from '../session/gameStore';
import { decodeSnapshotBody } from './decode';
import { GatewayError, getGame } from './gameGateway';

let active: EventSource | undefined;

export function openGameEventStream(
    gameId: GameId,
    onUnavailable: (status: number) => void,
    onReplaced: () => void,
): () => void {
    active?.close();
    let current = true;
    let stream: EventSource | undefined;
    const closed = (target: EventSource) => {
        target.close();
        if (active === target) active = undefined;
    };
    const recover = (target: EventSource) => {
        closed(target);
        getGameStore(gameId).setConnection('reconnecting');
        void getGame(gameId)
            .then(() => {
                if (current) open();
            })
            .catch((error: unknown) => {
                if (current) onUnavailable(error instanceof GatewayError ? error.problem.status : 0);
            });
    };
    const open = () => {
        if (!current) return;
        const next = new EventSource(`/api/v1/games/${encodeURIComponent(gameId)}/events`);
        stream = next;
        active = next;
        next.addEventListener('snapshot', (event) => {
            if (!current) return;
            try {
                const snapshot = decodeSnapshotBody((event as MessageEvent<string>).data, 'application/json');
                getGameStore(gameId).acceptSnapshot(snapshot);
                getGameStore(gameId).setConnection('connected');
            } catch {
                getGameStore(gameId).setConnection('reconnecting');
            }
        });
        next.addEventListener('closed', (event) => {
            if (!current) return;
            closed(next);
            try {
                const eventData = JSON.parse((event as MessageEvent<string>).data) as { reason?: unknown };
                if (eventData.reason === 'GAME_UNAVAILABLE') {
                    void getGame(gameId).catch((error: unknown) => {
                        if (current) onUnavailable(error instanceof GatewayError ? error.problem.status : 0);
                    });
                } else {
                    getGameStore(gameId).setConnection('offline');
                    if (eventData.reason === 'REPLACED') onReplaced();
                }
            } catch {
                getGameStore(gameId).setConnection('offline');
            }
        });
        next.onerror = () => {
            if (!current) return;
            if (next.readyState === EventSource.CONNECTING) {
                getGameStore(gameId).setConnection('reconnecting');
            } else if (next.readyState === EventSource.CLOSED) recover(next);
        };
    };
    open();
    return () => {
        current = false;
        if (stream) closed(stream);
    };
}
