import { expect, it, vi } from 'vitest';
import { createDeferredPwaGateway } from '../src/pwa/deferredGateway';
import { createInstallGateway } from '../src/pwa/installGateway';
import { createPwaGateway } from '../src/pwa/pwaGateway';

function installOffer(events: EventTarget) {
    const offer = new Event('beforeinstallprompt', { cancelable: true });
    events.dispatchEvent(offer);
}

it('replays an install offer to a late subscriber and stops after unsubscribe', () => {
    const events = new EventTarget();
    const gateway = createInstallGateway(events);
    installOffer(events);

    const late = vi.fn();
    const unsubscribe = gateway.onAvailabilityChange(late);
    expect(late).toHaveBeenCalledWith(true);

    unsubscribe();
    events.dispatchEvent(new Event('appinstalled'));
    expect(late).toHaveBeenCalledTimes(1);
});

it('replays a ready update to a late subscriber and stops after unsubscribe', () => {
    let needRefresh: (() => void) | undefined;
    const gateway = createPwaGateway((options) => {
        needRefresh = options.onNeedRefresh;
        return () => Promise.resolve();
    });
    needRefresh?.();

    const late = vi.fn();
    const unsubscribe = gateway.onUpdateReady(late);
    expect(late).toHaveBeenCalledOnce();

    unsubscribe();
    needRefresh?.();
    expect(late).toHaveBeenCalledOnce();
});

it('forwards a subscription made before registration and honours its unsubscribe', () => {
    const events = new EventTarget();
    let needRefresh: (() => void) | undefined;
    const gateway = createDeferredPwaGateway(events, { readyState: 'loading' }, () =>
        createPwaGateway((options) => {
            needRefresh = options.onNeedRefresh;
            return () => Promise.resolve();
        }),
    );
    const early = vi.fn();
    const gone = vi.fn();
    gateway.onUpdateReady(early);
    gateway.onUpdateReady(gone)();

    events.dispatchEvent(new Event('load'));
    needRefresh?.();

    expect(early).toHaveBeenCalledOnce();
    expect(gone).not.toHaveBeenCalled();
});
