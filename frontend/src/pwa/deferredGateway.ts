import type { PwaGateway } from './pwaGateway';

export function createDeferredPwaGateway(
    win: Pick<Window, 'addEventListener'>,
    doc: Pick<Document, 'readyState'>,
    register: () => PwaGateway,
): PwaGateway {
    const listeners = new Set<() => void>();
    let gateway: PwaGateway | null = null;
    const start = (): void => {
        gateway = register();
        listeners.forEach((listener) => gateway?.onUpdateReady(listener));
    };
    if (doc.readyState === 'complete') start();
    else win.addEventListener('load', start, { once: true });

    return {
        onUpdateReady(callback) {
            listeners.add(callback);
            gateway?.onUpdateReady(callback);
        },
        applyUpdate: () => gateway?.applyUpdate() ?? Promise.resolve(),
    };
}
