interface BeforeInstallPromptEvent extends Event {
    prompt(): Promise<void>;
    readonly userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

export type InstallOutcome = 'accepted' | 'dismissed' | 'unavailable';

export interface InstallGateway {
    onAvailabilityChange(callback: (available: boolean) => void): void;
    prompt(): Promise<InstallOutcome>;
}

export function createInstallGateway(win: Pick<Window, 'addEventListener'>): InstallGateway {
    let deferred: BeforeInstallPromptEvent | null = null;
    const listeners = new Set<(available: boolean) => void>();
    const setDeferred = (event: BeforeInstallPromptEvent | null): void => {
        deferred = event;
        listeners.forEach((listener) => {
            listener(event !== null);
        });
    };

    win.addEventListener('beforeinstallprompt', (event) => {
        event.preventDefault();
        setDeferred(event as BeforeInstallPromptEvent);
    });
    win.addEventListener('appinstalled', () => {
        setDeferred(null);
    });

    return {
        onAvailabilityChange(callback) {
            listeners.add(callback);
        },
        async prompt() {
            const event = deferred;
            if (event === null) return 'unavailable';
            await event.prompt();
            const { outcome } = await event.userChoice;
            setDeferred(null);
            return outcome;
        },
    };
}
