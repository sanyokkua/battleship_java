import { getGameStore, type GameId } from '../session/gameStore';
import { decodeSnapshotBody } from './decode';
import { GatewayError, getGame } from './gameGateway';

let active: EventSource | undefined;
const backoffMs = [1000, 2000, 4000, 8000] as const;

export function openGameEventStream(
    gameId: GameId,
    onUnavailable: (status: number) => void,
    onReplaced: () => void,
): () => void {
    active?.close();
    let current = true;
    let terminal = false;
    let recovering = false;
    let stream: EventSource | undefined;
    let terminalLookup: (() => void) | undefined;
    let timer = 0;
    let generation = 0;
    let retryIndex = 0;
    let wasHidden = document.visibilityState === 'hidden';
    const store = getGameStore(gameId);

    const clearTimer = () => {
        window.clearTimeout(timer);
        timer = 0;
    };
    const invalidateRecovery = () => {
        generation++;
        recovering = false;
        clearTimer();
    };
    const closeStream = (target = stream) => {
        if (!target) return;
        target.close();
        if (stream === target) stream = undefined;
        if (active === target) active = undefined;
    };
    const nextBackoff = () => {
        const delay = backoffMs[Math.min(retryIndex, backoffMs.length - 1)] ?? 8000;
        retryIndex++;
        return delay;
    };
    const schedule = (delayMs: number, action: () => void, allowTerminal = false) => {
        clearTimer();
        timer = window.setTimeout(() => {
            timer = 0;
            if (!current || (terminal && !allowTerminal)) return;
            if (!navigator.onLine) {
                store.setConnection('offline');
                return;
            }
            action();
        }, delayMs);
    };
    const unavailable = (status: number) => {
        if (!current || terminal) return;
        terminal = true;
        invalidateRecovery();
        closeStream();
        store.setNotice(undefined);
        onUnavailable(status);
    };
    const open = () => {
        if (!current || terminal || !navigator.onLine) return;
        closeStream();
        const next = new EventSource(`/api/v1/games/${encodeURIComponent(gameId)}/events`);
        stream = next;
        active = next;
        next.addEventListener('snapshot', (event) => {
            if (!current || terminal || stream !== next) return;
            try {
                const snapshot = decodeSnapshotBody((event as MessageEvent<string>).data, 'application/json');
                store.acceptSnapshot(snapshot);
                store.setConnection('connected');
                retryIndex = 0;
            } catch {
                store.setConnection('reconnecting');
            }
        });
        next.addEventListener('closed', (event) => {
            if (!current || terminal || stream !== next) return;
            let reason: unknown;
            try {
                reason = (JSON.parse((event as MessageEvent<string>).data) as { reason?: unknown }).reason;
            } catch {
                reason = undefined;
            }
            closeStream(next);
            if (reason === 'REPLACED') {
                terminal = true;
                invalidateRecovery();
                store.setConnection('offline');
                store.setNotice(undefined);
                onReplaced();
                return;
            }
            if (reason === 'GAME_UNAVAILABLE') {
                terminal = true;
                invalidateRecovery();
                const lookupUnavailable = () => {
                    if (!current) return;
                    const retryAtMs = store.getSnapshot().notice?.retryAtMs;
                    if (retryAtMs && retryAtMs > Date.now()) {
                        terminalLookup = lookupUnavailable;
                        schedule(retryAtMs - Date.now(), lookupUnavailable, true);
                        return;
                    }
                    if (!navigator.onLine) {
                        store.setConnection('offline');
                        terminalLookup = lookupUnavailable;
                        return;
                    }
                    clearTimer();
                    terminalLookup = undefined;
                    void getGame(gameId).catch((error: unknown) => {
                        if (!current) return;
                        if (error instanceof GatewayError && [429, 503].includes(error.problem.status)) {
                            store.setNotice(error.problem);
                        }
                        onUnavailable(error instanceof GatewayError ? error.problem.status : 0);
                    });
                };
                terminalLookup = lookupUnavailable;
                lookupUnavailable();
                return;
            }
            store.setConnection('reconnecting');
            recover(true, nextBackoff());
        });
        next.onerror = () => {
            if (!current || terminal || stream !== next) return;
            if (!navigator.onLine) {
                goOffline();
            } else if (next.readyState === EventSource.CONNECTING) {
                store.setConnection('reconnecting');
            } else if (next.readyState === EventSource.CLOSED) {
                closeStream(next);
                store.setConnection('reconnecting');
                recover(true, nextBackoff());
            }
        };
    };
    const recover = (reopen: boolean, reopenDelayMs: number) => {
        if (!current || terminal || recovering) return;
        if (!navigator.onLine) {
            goOffline();
            return;
        }
        const retryAtMs = store.getSnapshot().notice?.retryAtMs;
        if (retryAtMs && retryAtMs > Date.now()) {
            schedule(retryAtMs - Date.now(), () => {
                recover(reopen, reopenDelayMs);
            });
            return;
        }
        clearTimer();
        recovering = true;
        store.setConnection('reconnecting');
        const token = ++generation;
        void getGame(gameId)
            .then(() => {
                if (!current || terminal || token !== generation) return;
                recovering = false;
                store.setNotice(undefined);
                if (reopen) {
                    if (reopenDelayMs > 0) schedule(reopenDelayMs, open);
                    else open();
                }
            })
            .catch((error: unknown) => {
                if (!current || terminal || token !== generation) return;
                recovering = false;
                if (error instanceof GatewayError && [401, 404, 410].includes(error.problem.status)) {
                    unavailable(error.problem.status);
                    return;
                }
                if (error instanceof GatewayError && [429, 503].includes(error.problem.status)) {
                    store.setNotice(error.problem);
                    const retryAfter = error.problem.retryAfterSeconds;
                    schedule(retryAfter ? retryAfter * 1000 : nextBackoff(), () => {
                        recover(reopen, reopenDelayMs);
                    });
                    return;
                }
                schedule(nextBackoff(), () => {
                    recover(reopen, reopenDelayMs);
                });
            });
    };
    const goOffline = () => {
        if (!current || terminal) return;
        invalidateRecovery();
        closeStream();
        store.setConnection('offline');
    };
    const onOffline = () => {
        goOffline();
    };
    const onOnline = () => {
        if (!current || !navigator.onLine) return;
        if (terminal) {
            terminalLookup?.();
            return;
        }
        recover(true, 0);
    };
    const onVisibility = () => {
        if (document.visibilityState === 'hidden') {
            wasHidden = true;
            return;
        }
        if (!wasHidden) return;
        wasHidden = false;
        if (terminal) {
            terminalLookup?.();
            return;
        }
        if (!navigator.onLine) {
            goOffline();
            return;
        }
        recover(true, 0);
    };

    window.addEventListener('offline', onOffline);
    window.addEventListener('online', onOnline);
    document.addEventListener('visibilitychange', onVisibility);
    if (!navigator.onLine) store.setConnection('offline');
    else open();
    return () => {
        current = false;
        terminal = true;
        terminalLookup = undefined;
        invalidateRecovery();
        closeStream();
        window.removeEventListener('offline', onOffline);
        window.removeEventListener('online', onOnline);
        document.removeEventListener('visibilitychange', onVisibility);
    };
}
