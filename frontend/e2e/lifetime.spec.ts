import { expect, test } from './fixtures/stack.js';
import type { Page, Response } from '@playwright/test';
import type { components } from '../src/contract/generated/api';

type Snapshot = components['schemas']['GameSnapshot'];

async function createWaitingGame(page: Page, origin: string) {
    await page.goto(`${origin}/create`);
    await page.getByLabel('Captain name').fill('Host');
    const created = page.waitForResponse(
        (response) => response.request().method() === 'POST' && response.url().endsWith('/api/v1/games'),
    );
    await page.getByRole('button', { name: 'Create game' }).click();
    const snapshot = (await (await created).json()) as Snapshot;
    return { gameId: snapshot.gameId, invitationUrl: snapshot.invitationUrl };
}

async function returnToVisible(page: Page) {
    await page.evaluate(() => {
        Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' });
        document.dispatchEvent(new Event('visibilitychange'));
    });
}

function observeGameReads(page: Page, gameId: string, reads: Response[]) {
    page.on('response', (response) => {
        const url = new URL(response.url());
        if (response.request().method() === 'GET' && url.pathname === `/api/v1/games/${gameId}`) reads.push(response);
    });
}

const shortLifetime = {
    BATTLESHIP_IDLETIMEOUTSECONDS: '8',
    BATTLESHIP_SWEEPINTERVALSECONDS: '1',
    BATTLESHIP_RESULTRETENTIONSECONDS: '4',
    BATTLESHIP_PRESENCEINTERVALSECONDS: '2',
};

test.setTimeout(60_000);

test('hidden waiting game expires and is forgotten after retention', async ({ page, useStack }) => {
    const stack = await useStack('lifetime-hidden', { frontendPort: 4174, backendPort: 8081 }, shortLifetime);
    try {
        const { gameId } = await createWaitingGame(page, stack.origin);
        await page.evaluate(() => {
            Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' });
            document.dispatchEvent(new Event('visibilitychange'));
        });
        await page.waitForTimeout(10_000);
        await returnToVisible(page);
        await expect(page.getByRole('heading', { name: 'This game has expired.' })).toBeVisible({ timeout: 10_000 });
        await page.waitForTimeout(5_000);
        const expired = await page.request.get(`${stack.origin}/api/v1/games/${gameId}`);
        expect(expired.status()).toBe(404);
        await page.reload();
        await expect(page.getByRole('heading', { name: /This game is no longer available/ })).toBeVisible();
    } finally {
        await stack.stop();
    }
});

test('visible waiting game presence advances the displayed session expiry', async ({ page, useStack }) => {
    const stack = await useStack('lifetime-presence', { frontendPort: 4174, backendPort: 8081 }, shortLifetime);
    try {
        const { gameId } = await createWaitingGame(page, stack.origin);
        const expiry = page.locator('time[datetime]');
        await expect(expiry).toBeVisible();
        const initialExpiry = await expiry.getAttribute('datetime');
        if (initialExpiry === null) throw new Error('The waiting snapshot has no session expiry.');
        await expect.poll(() => expiry.getAttribute('datetime'), { timeout: 15_000 }).not.toBe(initialExpiry);
        const firstAdvancedExpiry = await expiry.getAttribute('datetime');
        if (firstAdvancedExpiry === null) throw new Error('The first presence update has no session expiry.');
        await page.waitForTimeout(9_000);
        const stillWaiting = await page.request.get(`${stack.origin}/api/v1/games/${gameId}`);
        expect(stillWaiting.status()).toBe(200);
        expect(((await stillWaiting.json()) as Snapshot).phase).toBe('WAITING');
        expect(Date.parse((await expiry.getAttribute('datetime')) ?? '')).toBeGreaterThan(
            Date.parse(firstAdvancedExpiry),
        );
        await expect(page.getByText(/Session expires in:/)).toBeVisible();
    } finally {
        await stack.stop();
    }
});

test('backend restart during placement clears the remembered game and offers a new game', async ({
    page,
    browser,
    useStack,
}) => {
    const stack = await useStack('lifetime-restart', { frontendPort: 4176, backendPort: 8083 });
    const guest = await browser.newPage();
    try {
        const { gameId, invitationUrl } = await createWaitingGame(page, stack.origin);
        if (!invitationUrl) throw new Error('The waiting game has no invitation URL.');
        await guest.goto(`${stack.origin}/join/${gameId}${new URL(invitationUrl).hash}`);
        await guest.getByLabel('Captain name').fill('Guest');
        await guest.getByRole('button', { name: 'Join game' }).click();
        await expect(page.getByRole('heading', { name: 'Place your fleet' })).toBeVisible();
        await stack.restartBackend();
        await page.reload();
        await expect(page.getByRole('heading', { name: /This game is no longer available/ })).toBeVisible();
        await expect(page.getByRole('button', { name: 'Create new game' })).toBeVisible();
        expect(await page.evaluate(() => localStorage.getItem('battleship.currentGameId'))).toBeNull();
    } finally {
        await guest.close();
        await stack.stop();
    }
});

test('Home does not offer a remembered game and GAME_UNAVAILABLE reads once without reopening events', async ({
    page,
    useStack,
}) => {
    const stack = await useStack(
        'lifetime-forgotten',
        { frontendPort: 4174, backendPort: 8081 },
        { ...shortLifetime, BATTLESHIP_RATELIMIT_READGAMEPERMINUTE: '1000' },
    );
    try {
        const { gameId } = await createWaitingGame(page, stack.origin);
        await page.goto(stack.origin);
        await expect(page.getByRole('button', { name: 'Return to your game' })).toHaveCount(0);
        await page.addInitScript(() => {
            // eslint-disable-next-line @typescript-eslint/unbound-method -- Keep the native method's instance receiver.
            const original = EventSource.prototype.addEventListener;
            EventSource.prototype.addEventListener = function (
                this: EventSource,
                type: string,
                listener: EventListenerOrEventListenerObject,
                options?: boolean | AddEventListenerOptions,
            ) {
                if (type === 'closed' && typeof listener === 'function') {
                    const observed = function (this: EventSource, event: Event) {
                        const target = window as Window & {
                            observedStreamClosures?: string[];
                            streamClosedAt?: number;
                        };
                        target.observedStreamClosures ??= [];
                        target.observedStreamClosures.push((event as MessageEvent<string>).data);
                        target.streamClosedAt = Date.now();
                        listener.call(this, event);
                    };
                    original.call(this, type, observed, options);
                    return;
                }
                original.call(this, type, listener, options);
            } as typeof EventSource.prototype.addEventListener;
        });
        const reads: Response[] = [];
        const gameReadRequests: number[] = [];
        let eventRequests = 0;
        observeGameReads(page, gameId, reads);
        page.on('request', (request) => {
            const url = new URL(request.url());
            if (request.method() === 'GET' && url.pathname === `/api/v1/games/${gameId}`)
                gameReadRequests.push(Date.now());
        });
        page.on('request', (request) => {
            if (new URL(request.url()).pathname.endsWith('/events')) eventRequests++;
        });
        await page.goto(`${stack.origin}/games/${gameId}`);
        await expect(page.getByRole('heading', { name: 'Waiting for an opponent' })).toBeVisible();
        await expect.poll(() => reads.length, { timeout: 5000 }).toBeGreaterThan(0);
        await expect.poll(() => eventRequests, { timeout: 5000 }).toBeGreaterThan(0);
        reads.length = 0;
        gameReadRequests.length = 0;
        const initialEventRequests = eventRequests;
        await page.evaluate(() => {
            Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' });
            document.dispatchEvent(new Event('visibilitychange'));
        });
        await expect
            .poll(
                () =>
                    page.evaluate(
                        () => (window as Window & { observedStreamClosures?: string[] }).observedStreamClosures ?? [],
                    ),
                { timeout: 15_000 },
            )
            .toContain('{"reason":"GAME_UNAVAILABLE"}');
        const closedAt = await page.evaluate(() => (window as Window & { streamClosedAt?: number }).streamClosedAt);
        if (closedAt === undefined) throw new Error('The stream close timestamp was not recorded.');
        await expect.poll(() => reads.length, { timeout: 10_000 }).toBeGreaterThan(0);
        expect([404, 410]).toContain(reads[0].status());
        await page.waitForTimeout(250);
        expect(gameReadRequests.filter((requestedAt) => requestedAt >= closedAt)).toHaveLength(1);
        await page.waitForTimeout(2_000);
        expect(eventRequests).toBe(initialEventRequests);
    } finally {
        await stack.stop();
    }
});
