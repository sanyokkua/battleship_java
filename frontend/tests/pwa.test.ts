import { createDeferredPwaGateway } from '../src/pwa/deferredGateway';
import { createInstallGateway } from '../src/pwa/installGateway';
import { createPwaGateway } from '../src/pwa/pwaGateway';

it('registers PWA only after load and forwards update requests', async () => {
    const events = new EventTarget();
    const win: Pick<Window, 'addEventListener'> = events;
    let registered = false;
    let updateReady: (() => void) | undefined;
    let applied = false;
    const gateway = createDeferredPwaGateway(win, { readyState: 'loading' }, () => {
        registered = true;
        return {
            onUpdateReady: (listener) => {
                updateReady = listener;
            },
            applyUpdate: () => {
                applied = true;
                return Promise.resolve();
            },
        };
    });
    let prompted = false;
    gateway.onUpdateReady(() => {
        prompted = true;
    });

    expect(registered).toBe(false);
    events.dispatchEvent(new Event('load'));
    updateReady?.();
    await gateway.applyUpdate();

    expect(registered).toBe(true);
    expect(prompted).toBe(true);
    expect(applied).toBe(true);
});

it('activates a waiting service worker with a reload only when requested', async () => {
    let onNeedRefresh: (() => void) | undefined;
    let reload = false;
    const gateway = createPwaGateway((options) => {
        onNeedRefresh = options.onNeedRefresh;
        return (reloadPage) => {
            reload = reloadPage ?? false;
            return Promise.resolve();
        };
    });
    let available = false;
    gateway.onUpdateReady(() => {
        available = true;
    });

    onNeedRefresh?.();
    expect(available).toBe(true);
    expect(reload).toBe(false);
    await gateway.applyUpdate();
    expect(reload).toBe(true);
});

it('shows the native install prompt only after the browser offers it', async () => {
    const events = new EventTarget();
    const win: Pick<Window, 'addEventListener'> = events;
    const gateway = createInstallGateway(win);
    const availability: boolean[] = [];
    gateway.onAvailabilityChange((available) => availability.push(available));
    let prompted = false;
    let prevented = false;
    const offer = new Event('beforeinstallprompt', { cancelable: true }) as Event & {
        prompt: () => Promise<void>;
        userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
    };
    offer.prompt = () => {
        prompted = true;
        return Promise.resolve();
    };
    offer.userChoice = Promise.resolve({ outcome: 'accepted' });
    const preventDefault = offer.preventDefault.bind(offer);
    offer.preventDefault = () => {
        prevented = true;
        preventDefault();
    };

    events.dispatchEvent(offer);
    expect(availability).toEqual([true]);
    expect(prevented).toBe(true);
    expect(await gateway.prompt()).toBe('accepted');
    expect(prompted).toBe(true);
    expect(availability).toEqual([true, false]);
});
