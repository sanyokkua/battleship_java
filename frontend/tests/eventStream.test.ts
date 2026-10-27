import { afterEach, expect, it, vi } from 'vitest';
import { openGameEventStream } from '../src/gateway/eventStream';
import { getGameStore, type GameId } from '../src/session/gameStore';

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

afterEach(() => vi.unstubAllGlobals());

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

it('does not label an unknown closed reason as a replaced stream', () => {
    vi.stubGlobal('EventSource', ControlledEventSource);
    const gameId = 'unknown-close-test' as GameId;
    const onReplaced = vi.fn();
    const close = openGameEventStream(gameId, vi.fn(), onReplaced);

    const source = ControlledEventSource.latest;
    if (!source) throw new Error('EventSource constructor was not called.');
    source.emit('closed', JSON.stringify({ reason: 'FUTURE_REASON' }));

    expect(getGameStore(gameId).getSnapshot().connection).toBe('offline');
    expect(onReplaced).not.toHaveBeenCalled();
    close();
});
