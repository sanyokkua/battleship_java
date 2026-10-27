import type { components } from '../contract/generated/api';
import { snapshotGate, type GameSnapshot } from './snapshotGate';

export type Meta = components['schemas']['Meta'];
export type GameId = components['parameters']['GameId'];
export interface GameState {
    snapshot: GameSnapshot | undefined;
    meta: Meta | undefined;
    clockOffsetMs: number;
    connection: 'connected' | 'reconnecting' | 'offline';
}
export interface GameStore {
    subscribe(listener: () => void): () => void;
    getSnapshot(): GameState;
    setMeta(meta: Meta): void;
    acceptSnapshot(snapshot: GameSnapshot): void;
    setConnection(connection: GameState['connection']): void;
}

export function createGameStore(): GameStore {
    let state: GameState = { snapshot: undefined, meta: undefined, clockOffsetMs: 0, connection: 'connected' };
    const listeners = new Set<() => void>();
    const publish = (next: GameState) => {
        state = next;
        for (const listener of listeners) listener();
    };
    return {
        subscribe(listener) {
            listeners.add(listener);
            return () => listeners.delete(listener);
        },
        getSnapshot: () => state,
        setMeta(meta) {
            publish({ ...state, meta, clockOffsetMs: Date.parse(meta.serverTime) - Date.now() });
        },
        acceptSnapshot(snapshot) {
            const accepted = snapshotGate.accept(state.snapshot, snapshot);
            if (accepted === state.snapshot) return;
            publish({ ...state, snapshot: accepted, clockOffsetMs: Date.parse(accepted.serverTime) - Date.now() });
        },
        setConnection(connection) {
            if (connection !== state.connection) publish({ ...state, connection });
        },
    };
}

const stores = new Map<GameId, GameStore>();
export function getGameStore(gameId: GameId): GameStore {
    let store = stores.get(gameId);
    if (!store) {
        store = createGameStore();
        stores.set(gameId, store);
    }
    return store;
}
