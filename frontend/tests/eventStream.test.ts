import { afterEach, expect, it, vi } from 'vitest';
import { getGame } from '../src/gateway/gameGateway';
import { openGameEventStream } from '../src/gateway/eventStream';
import { getGameStore, type GameId } from '../src/session/gameStore';

vi.mock('../src/gateway/gameGateway', () => ({
    GatewayError: class GatewayError extends Error {
        constructor(readonly problem: { status: number; retryAfterSeconds?: number }) {
            super();
        }
    },
    getGame: vi.fn().mockResolvedValue({}),
}));
const visibilityDescriptor = Object.getOwnPropertyDescriptor(document, 'visibilityState');

class ControlledEventSource {
    static readonly CONNECTING = 0;
    static readonly OPEN = 1;
    static readonly CLOSED = 2;
    readyState = ControlledEventSource.OPEN;
    onerror: ((this: EventSource, event: Event) => unknown) | null = null;
    static latest: ControlledEventSource | undefined;
    private readonly listeners = new Map<string, EventListenerOrEventListenerObject>();

    constructor() {
        ControlledEventSource.latest = this;
    }

    addEventListener(type: string, listener: EventListenerOrEventListenerObject) {
        this.listeners.set(type, listener);
    }

    close() {
        this.readyState = ControlledEventSource.CLOSED;
    }

    failIn(state: number) {
        this.readyState = state;
        this.onerror?.call(this as unknown as EventSource, new Event('error'));
    }

    emit(type: string, data: string) {
        const listener = this.listeners.get(type);
        if (!listener) throw new Error(`No listener registered for ${type}.`);
        const event = new MessageEvent(type, { data });
        if (typeof listener === 'function') listener(event);
        else listener.handleEvent(event);
    }
}

afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
    vi.mocked(getGame).mockClear();
    if (visibilityDescriptor) Object.defineProperty(document, 'visibilityState', visibilityDescriptor);
    else Reflect.deleteProperty(document, 'visibilityState');
});

it('keeps a GAME_UNAVAILABLE lookup due after going offline through its Retry-After deadline', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-28T12:00:00.000Z'));
    let online = true;
    vi.stubGlobal('navigator', {
        get onLine() {
            return online;
        },
    });
    vi.stubGlobal('EventSource', ControlledEventSource);
    vi.mocked(getGame).mockResolvedValue({} as Awaited<ReturnType<typeof getGame>>);
    const gameId = 'terminal-retry-test' as GameId;
    const onUnavailable = vi.fn();
    const store = getGameStore(gameId);
    store.setNotice({
        status: 429,
        code: 'rate-limit-exceeded',
        messageKey: 'problem.rateLimited',
        retryAfterSeconds: 30,
    });
    const close = openGameEventStream(gameId, onUnavailable, vi.fn());
    const source = ControlledEventSource.latest;
    if (!source) throw new Error('EventSource constructor was not called.');
    source.emit('closed', JSON.stringify({ reason: 'GAME_UNAVAILABLE' }));

    online = false;
    window.dispatchEvent(new Event('offline'));
    await vi.advanceTimersByTimeAsync(30_000);
    expect(getGame).not.toHaveBeenCalled();

    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' });
    document.dispatchEvent(new Event('visibilitychange'));
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' });
    document.dispatchEvent(new Event('visibilitychange'));
    expect(getGame).not.toHaveBeenCalled();

    online = true;
    window.dispatchEvent(new Event('online'));
    await Promise.resolve();
    expect(getGame).toHaveBeenCalledTimes(1);
    expect(ControlledEventSource.latest).toBe(source);
    expect(onUnavailable).not.toHaveBeenCalled();
    close();
});

it('marks a live game reconnecting when EventSource reports CONNECTING', () => {
    vi.stubGlobal('EventSource', ControlledEventSource);
    const gameId = 'connecting-test' as GameId;
    const close = openGameEventStream(gameId, vi.fn(), vi.fn());

    const source = ControlledEventSource.latest;
    if (!source) throw new Error('EventSource constructor was not called.');
    source.failIn(ControlledEventSource.CONNECTING);

    expect(getGameStore(gameId).getSnapshot().connection).toBe('reconnecting');
    close();
});

it('does not label an unknown closed reason as offline or replaced', () => {
    vi.stubGlobal('EventSource', ControlledEventSource);
    const gameId = 'unknown-close-test' as GameId;
    const onReplaced = vi.fn();
    const close = openGameEventStream(gameId, vi.fn(), onReplaced);

    const source = ControlledEventSource.latest;
    if (!source) throw new Error('EventSource constructor was not called.');
    source.emit('closed', JSON.stringify({ reason: 'FUTURE_REASON' }));

    expect(getGameStore(gameId).getSnapshot().connection).toBe('reconnecting');
    expect(onReplaced).not.toHaveBeenCalled();
    close();
});
