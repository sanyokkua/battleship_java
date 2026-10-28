/** The subset of `registerSW` (vite-plugin-pwa) the gateway relies on. */
export type RegisterServiceWorker = (options: { onNeedRefresh: () => void }) => (reloadPage?: boolean) => Promise<void>;

export interface PwaGateway {
    onUpdateReady(callback: () => void): void;
    applyUpdate(): Promise<void>;
}

export function createPwaGateway(register: RegisterServiceWorker): PwaGateway {
    const listeners = new Set<() => void>();
    const updateSW = register({
        onNeedRefresh: () => {
            listeners.forEach((listener) => {
                listener();
            });
        },
    });
    return {
        onUpdateReady(callback) {
            listeners.add(callback);
        },
        applyUpdate: () => updateSW(true),
    };
}
