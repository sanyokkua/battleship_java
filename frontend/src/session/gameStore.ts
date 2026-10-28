import type { components } from '../contract/generated/api';
import type { GatewayProblem } from '../gateway/problems';
import { snapshotGate, type GameSnapshot } from './snapshotGate';

export type Meta = components['schemas']['Meta'];
export type GameId = components['parameters']['GameId'];
export interface GameState {
    snapshot: GameSnapshot | undefined;
    meta: Meta | undefined;
    clockOffsetMs: number;
    connection: 'connected' | 'reconnecting' | 'offline';
    action: components['schemas']['CommandRequest'] | undefined;
    checking: boolean;
    notice: (GatewayProblem & { retryAtMs?: number }) | undefined;
}
export interface GameStore {
    subscribe(listener: () => void): () => void;
    getSnapshot(): GameState;
    setMeta(meta: Meta): void;
    acceptSnapshot(snapshot: GameSnapshot): void;
    setConnection(connection: GameState['connection']): void;
    setNotice(notice?: GatewayProblem): void;
    beginAction(command: components['schemas']['Command']): components['schemas']['CommandRequest'] | undefined;
    retryAction(): components['schemas']['CommandRequest'] | undefined;
    checkAction(): void;
    settleAction(notice?: GatewayProblem): void;
}

export function createGameStore(): GameStore {
    let state: GameState = {
        snapshot: undefined,
        meta: undefined,
        clockOffsetMs: 0,
        connection: 'connected',
        action: undefined,
        checking: false,
        notice: undefined,
    };
    const listeners = new Set<() => void>();
    const makeNotice = (notice?: GatewayProblem) =>
        notice
            ? {
                  ...notice,
                  ...(notice.retryAfterSeconds ? { retryAtMs: Date.now() + notice.retryAfterSeconds * 1000 } : {}),
              }
            : undefined;
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
        beginAction(command) {
            if (state.action) return undefined;
            const action = { commandId: crypto.randomUUID(), command };
            publish({ ...state, action, checking: false, notice: undefined });
            return action;
        },
        retryAction() {
            if (!state.action || !state.checking) return undefined;
            const action = state.action;
            publish({ ...state, checking: false });
            return action;
        },
        checkAction() {
            if (state.action) publish({ ...state, checking: true });
        },
        settleAction(notice) {
            publish({
                ...state,
                action: undefined,
                checking: false,
                notice: makeNotice(notice),
            });
        },
        setConnection(connection) {
            if (connection !== state.connection) publish({ ...state, connection });
        },
        setNotice(notice) {
            publish({ ...state, notice: makeNotice(notice) });
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
