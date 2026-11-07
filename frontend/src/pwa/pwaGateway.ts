/** The subset of `registerSW` (vite-plugin-pwa) the gateway relies on. */
export type RegisterServiceWorker = (options: { onNeedRefresh: () => void }) => (reloadPage?: boolean) => Promise<void>;

export interface PwaGateway {
    /** Calls back when an update is ready (immediately if one already is) and returns an unsubscribe. */
    onUpdateReady(callback: () => void): () => void;
    applyUpdate(): Promise<void>;
}

export function createPwaGateway(register: RegisterServiceWorker): PwaGateway {
    const listeners = new Set<() => void>();
    let updateReady = false;
    const updateSW = register({
        onNeedRefresh: () => {
            updateReady = true;
            listeners.forEach((listener) => {
                listener();
            });
        },
    });
    return {
        onUpdateReady(callback) {
            listeners.add(callback);
            if (updateReady) callback();
            return () => {
                listeners.delete(callback);
            };
        },
        applyUpdate: () => updateSW(true),
    };
}
