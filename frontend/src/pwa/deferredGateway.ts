import type { PwaGateway } from './pwaGateway';

export function createDeferredPwaGateway(
    win: Pick<Window, 'addEventListener'>,
    doc: Pick<Document, 'readyState'>,
    register: () => PwaGateway,
): PwaGateway {
    // Each subscriber's unsubscribe from the real gateway, set once registration has happened.
    const subscribers = new Map<() => void, (() => void) | undefined>();
    let gateway: PwaGateway | null = null;
    const start = (): void => {
        gateway = register();
        for (const callback of subscribers.keys()) subscribers.set(callback, gateway.onUpdateReady(callback));
    };
    if (doc.readyState === 'complete') start();
    else win.addEventListener('load', start, { once: true });

    return {
        onUpdateReady(callback) {
            subscribers.set(callback, gateway?.onUpdateReady(callback));
            return () => {
                subscribers.get(callback)?.();
                subscribers.delete(callback);
            };
        },
        applyUpdate: () => gateway?.applyUpdate() ?? Promise.resolve(),
    };
}
