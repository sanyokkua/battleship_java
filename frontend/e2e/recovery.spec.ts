import { expect, test } from './fixtures/stack.js';
import type { Page, Response } from '@playwright/test';
import { prepareGame, readGame, type Snapshot } from './helpers/playGame.js';

const gameplayTurn = 'Your turn · Tap an enemy cell';

async function findShooter(page: Page, guest: Page) {
    if (
        await page
            .getByText(gameplayTurn, { exact: true })
            .isVisible()
            .catch(() => false)
    )
        return page;
    await expect(guest.getByText(gameplayTurn, { exact: true })).toBeVisible();
    return guest;
}

async function expectBoardsMatchSnapshot(page: Page, gameId: string) {
    const snapshot = await readGame(page, gameId);
    const yours = await page
        .getByRole('grid', { name: 'Your fleet' })
        .locator('[role="gridcell"]')
        .evaluateAll((cells) => cells.map((cell) => cell.getAttribute('data-state')));
    const opponent = await page
        .getByRole('grid', { name: 'Enemy waters' })
        .locator('[role="gridcell"]')
        .evaluateAll((cells) => cells.map((cell) => cell.getAttribute('data-state')));
    expect(yours).toEqual(snapshot.yourBoard.grid.flat().map((cell) => cell.toLowerCase()));
    expect(opponent).toEqual(snapshot.opponentBoard.grid.flat().map((cell) => cell.toLowerCase()));
}

function observeGameReads(page: Page, gameId: string, target: Response[]) {
    page.on('response', (response) => {
        const url = new URL(response.url());
        if (response.request().method() === 'GET' && url.pathname === '/api/v1/games/' + gameId) target.push(response);
    });
}

async function toggleOffline(page: Page, offline: boolean) {
    await page.context().setOffline(offline);
}

async function returnToVisible(page: Page) {
    await page.evaluate(() => {
        Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' });
        document.dispatchEvent(new Event('visibilitychange'));
        Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' });
        document.dispatchEvent(new Event('visibilitychange'));
    });
}

async function fireOnce(page: Page) {
    const command = page.waitForResponse((response) => response.url().endsWith('/commands'));
    await page
        .getByRole('grid', { name: 'Enemy waters' })
        .getByRole('gridcell', { name: /, unknown$/ })
        .first()
        .click();
    const response = await command;
    expect(response.ok()).toBeTruthy();
    return (await response.json()) as Snapshot;
}

test.setTimeout(100_000);

test('offline disables firing and Refresh, then reconnects without resending a command', async ({
    page,
    browser,
    stack,
}) => {
    const { guest, gameId } = await prepareGame(page, browser, stack.origin, 'Sea Battle');
    try {
        const shooter = await findShooter(page, guest);
        const commands: string[] = [];
        shooter.on('request', (request) => {
            if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/commands')) {
                commands.push(request.postData() ?? '');
            }
        });
        const enemyCells = shooter.getByRole('grid', { name: 'Enemy waters' }).getByRole('gridcell');
        await toggleOffline(shooter, true);
        await expect(shooter.getByText('Offline', { exact: true })).toBeVisible();
        await expect(shooter.getByRole('banner').getByRole('button', { name: 'Refresh' })).toBeDisabled();
        await expect
            .poll(async () => {
                const count = await enemyCells.count();
                let disabled = 0;
                for (let index = 0; index < count; index++) {
                    if ((await enemyCells.nth(index).getAttribute('aria-disabled')) === 'true') disabled++;
                }
                return disabled;
            })
            .toBe(await enemyCells.count());
        const commandsBeforeReconnect = commands.length;
        await toggleOffline(shooter, false);
        await expect(shooter.getByText('Connected', { exact: true })).toBeVisible({ timeout: 10_000 });
        await expect(shooter.getByRole('banner').getByRole('button', { name: 'Refresh' })).toBeEnabled();
        expect(commands).toHaveLength(commandsBeforeReconnect);
        await expectBoardsMatchSnapshot(shooter, gameId);
    } finally {
        await guest.close();
    }
});

test('a replaced stream stays closed after another tab opens the same game', async ({ page, browser, stack }) => {
    const { guest, gameId } = await prepareGame(page, browser, stack.origin, 'Sea Battle');
    let firstTabOpens = 0;
    page.on('request', (request) => {
        if (new URL(request.url()).pathname.endsWith('/events')) firstTabOpens++;
    });
    const secondTab = await page.context().newPage();
    try {
        await secondTab.goto(stack.origin + '/games/' + gameId);
        await expect(secondTab.getByRole('grid', { name: 'Enemy waters' })).toBeVisible();
        await expect(page.getByText('Open in another tab', { exact: true })).toBeVisible();
        const opensAfterReplacement = firstTabOpens;
        await page.waitForTimeout(2500);
        expect(firstTabOpens).toBe(opensAfterReplacement);
    } finally {
        await secondTab.close();
        await guest.close();
    }
});

test('returning to a visible tab fetches once and reopens the stream', async ({ page, browser, stack }) => {
    const { guest, gameId } = await prepareGame(page, browser, stack.origin, 'Sea Battle');
    const reads: Response[] = [];
    let streamOpens = 0;
    observeGameReads(page, gameId, reads);
    page.on('request', (request) => {
        if (new URL(request.url()).pathname.endsWith('/events')) streamOpens++;
    });
    try {
        await page.evaluate(() => {
            Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' });
            document.dispatchEvent(new Event('visibilitychange'));
        });
        const readsWhenHidden = reads.length;
        const streamsWhenHidden = streamOpens;
        await page.evaluate(() => {
            Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' });
            document.dispatchEvent(new Event('visibilitychange'));
        });
        await expect.poll(() => reads.length, { timeout: 5000 }).toBe(readsWhenHidden + 1);
        await expect.poll(() => streamOpens, { timeout: 5000 }).toBeGreaterThan(streamsWhenHidden);
        expect(reads).toHaveLength(readsWhenHidden + 1);
    } finally {
        await guest.close();
    }
});

test('a 429 recovery waits for Retry-After before reading the game again', async ({ page, browser, useStack }) => {
    const stack = await useStack(
        'recovery-read-limit',
        { frontendPort: 4175, backendPort: 8082 },
        { BATTLESHIP_RATELIMIT_READGAMEPERMINUTE: '2' },
    );
    try {
        const { guest, gameId } = await prepareGame(page, browser, stack.origin, 'Sea Battle');
        const reads: Response[] = [];
        observeGameReads(page, gameId, reads);
        try {
            const shooter = await findShooter(page, guest);
            if (shooter === page) {
                let snapshot = await fireOnce(page);
                while (snapshot.allowedActions.includes('FIRE')) snapshot = await fireOnce(page);
                await expect(guest.getByText(gameplayTurn, { exact: true })).toBeVisible();
            }

            await returnToVisible(page);
            await expect.poll(() => reads.length, { timeout: 5000 }).toBe(1);
            expect(reads[0].status()).toBe(200);
            await returnToVisible(page);
            await expect.poll(() => reads.length, { timeout: 5000 }).toBe(2);
            const refused = reads[1];
            expect(refused.status()).toBe(429);
            const body = (await refused.json()) as { retryAfterSeconds: number };
            const waitSeconds = body.retryAfterSeconds;
            const retryDeadline = Date.now() + waitSeconds * 1000;
            await expect(page.getByRole('alert')).toContainText('Too many requests');
            await expect(page.getByRole('alert')).toContainText('Wait ' + String(waitSeconds) + ' seconds');

            const beforeOpponentShot = await page
                .getByRole('grid', { name: 'Your fleet' })
                .locator('[role="gridcell"]')
                .evaluateAll((cells) => cells.map((cell) => cell.getAttribute('data-state')));
            await fireOnce(guest);
            await expect
                .poll(() =>
                    page
                        .getByRole('grid', { name: 'Your fleet' })
                        .locator('[role="gridcell"]')
                        .evaluateAll((cells) => cells.map((cell) => cell.getAttribute('data-state'))),
                )
                .not.toEqual(beforeOpponentShot);
            await expect(page.getByRole('alert')).toContainText('Wait ' + String(waitSeconds) + ' seconds');

            const headerRefreshDisabled = await page
                .getByRole('banner')
                .getByRole('button', { name: 'Refresh' })
                .isDisabled();
            await page.evaluate(() => window.dispatchEvent(new CustomEvent('game-refresh')));
            await page.waitForTimeout(1000);
            expect({ headerRefreshDisabled, gameReads: reads.length }).toEqual({
                headerRefreshDisabled: true,
                gameReads: 2,
            });

            await toggleOffline(page, true);
            await expect(page.getByText('Offline', { exact: true })).toBeVisible();
            await toggleOffline(page, false);
            await returnToVisible(page);
            await page.waitForTimeout(1000);
            expect(reads).toHaveLength(2);
            await page.waitForTimeout(Math.max(0, retryDeadline - Date.now() - 1000));
            expect(reads).toHaveLength(2);
            await expect.poll(() => reads.length, { timeout: 5000 }).toBe(3);
            expect(reads[2].status()).toBe(200);
        } finally {
            await guest.close();
        }
    } finally {
        await stack.stop();
    }
});

test('manual Refresh honors Retry-After without retrying automatically', async ({ page, browser, useStack }) => {
    const stack = await useStack(
        'manual-refresh-read-limit',
        { frontendPort: 4175, backendPort: 8082 },
        { BATTLESHIP_RATELIMIT_READGAMEPERMINUTE: '2' },
    );
    try {
        const { guest, gameId } = await prepareGame(page, browser, stack.origin, 'Sea Battle');
        const reads: Response[] = [];
        observeGameReads(page, gameId, reads);
        try {
            await returnToVisible(page);
            await expect.poll(() => reads.length, { timeout: 5000 }).toBe(1);
            expect(reads[0].status()).toBe(200);

            const refresh = page.getByRole('banner').getByRole('button', { name: 'Refresh' });
            await expect(refresh).toBeEnabled();
            await refresh.click();
            await expect.poll(() => reads.length, { timeout: 5000 }).toBe(2);
            expect(reads[1].status()).toBe(429);
            const body = (await reads[1].json()) as { retryAfterSeconds: number };
            const waitSeconds = body.retryAfterSeconds;
            const retryDeadline = Date.now() + waitSeconds * 1000;
            const waitHintVisible = await page
                .getByRole('alert')
                .filter({ hasText: 'Wait ' + String(waitSeconds) + ' seconds' })
                .isVisible()
                .catch(() => false);
            const refreshDisabled = await refresh.isDisabled();
            await page.evaluate(() => window.dispatchEvent(new CustomEvent('game-refresh')));
            await page.waitForTimeout(1000);
            expect({ waitHintVisible, refreshDisabled, gameReads: reads.length }).toEqual({
                waitHintVisible: true,
                refreshDisabled: true,
                gameReads: 2,
            });

            await page.waitForTimeout(Math.max(0, retryDeadline - Date.now() + 500));
            expect(reads).toHaveLength(2);
            await expect(refresh).toBeEnabled();
            await refresh.click();
            await expect.poll(() => reads.length, { timeout: 5000 }).toBe(3);
            expect(reads[2].status()).toBe(200);
        } finally {
            await guest.close();
        }
    } finally {
        await stack.stop();
    }
});

test('GAME_UNAVAILABLE defers its single lookup until Retry-After expires', async ({ page, browser, useStack }) => {
    const stack = await useStack(
        'recovery-unavailable-read-limit',
        { frontendPort: 4175, backendPort: 8082 },
        {
            BATTLESHIP_IDLETIMEOUTSECONDS: '8',
            BATTLESHIP_SWEEPINTERVALSECONDS: '1',
            BATTLESHIP_RESULTRETENTIONSECONDS: '4',
            BATTLESHIP_PRESENCEINTERVALSECONDS: '2',
            BATTLESHIP_RATELIMIT_READGAMEPERMINUTE: '2',
        },
    );
    try {
        const reads: Response[] = [];
        let streamOpens = 0;
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
                        const target = window as Window & { observedStreamClosures?: string[] };
                        target.observedStreamClosures ??= [];
                        target.observedStreamClosures.push((event as MessageEvent<string>).data);
                        listener.call(this, event);
                    };
                    original.call(this, type, observed, options);
                    return;
                }
                original.call(this, type, listener, options);
            } as typeof EventSource.prototype.addEventListener;
        });
        page.on('response', (response) => {
            const url = new URL(response.url());
            if (response.request().method() === 'GET' && /^\/api\/v1\/games\/[^/]+$/.test(url.pathname)) {
                reads.push(response);
            }
        });
        page.on('request', (request) => {
            if (new URL(request.url()).pathname.endsWith('/events')) streamOpens++;
        });
        const { guest } = await prepareGame(page, browser, stack.origin, 'Sea Battle');
        let guestOpen = true;
        try {
            const refresh = page.getByRole('banner').getByRole('button', { name: 'Refresh' });
            await expect.poll(() => reads.length, { timeout: 5000 }).toBeGreaterThan(0);
            expect(reads[0].status()).toBe(200);

            await refresh.click();
            await expect.poll(() => reads.length, { timeout: 5000 }).toBe(2);
            expect(reads[1].status()).toBe(200);

            await refresh.click();
            await expect.poll(() => reads.length, { timeout: 5000 }).toBe(3);
            expect(reads[2].status()).toBe(429);
            const body = (await reads[2].json()) as { retryAfterSeconds: number };
            await expect(page.getByRole('alert').filter({ hasText: 'Wait ' })).toBeVisible();
            const retryDeadline = Date.now() + body.retryAfterSeconds * 1000;
            const initialStreamOpens = streamOpens;
            expect(initialStreamOpens).toBeGreaterThan(0);

            await page.evaluate(() => {
                Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' });
                document.dispatchEvent(new Event('visibilitychange'));
            });
            await guest.close();
            guestOpen = false;

            await expect
                .poll(
                    () =>
                        page.evaluate(
                            () =>
                                (window as Window & { observedStreamClosures?: string[] }).observedStreamClosures ?? [],
                        ),
                    {
                        timeout: 20_000,
                    },
                )
                .toContain('{"reason":"GAME_UNAVAILABLE"}');
            await expect(page.getByRole('alert').filter({ hasText: 'Wait ' })).toBeVisible();
            expect(
                Date.now(),
                JSON.stringify({
                    retryAfterSeconds: body.retryAfterSeconds,
                    statuses: reads.map((read) => read.status()),
                }),
            ).toBeLessThan(retryDeadline);
            expect(reads).toHaveLength(3);
            await expect.poll(() => reads.length, { timeout: 5000 }).toBe(3);
            await page.waitForTimeout(Math.max(0, retryDeadline - Date.now() - 1000));
            expect(reads).toHaveLength(3);
            expect(streamOpens).toBe(initialStreamOpens);

            await expect.poll(() => reads.length, { timeout: 5000 }).toBe(4);
            expect([404, 410]).toContain(reads[3].status());
            await page.waitForTimeout(1000);
            expect(reads).toHaveLength(4);
            expect(streamOpens).toBe(initialStreamOpens);
        } finally {
            if (guestOpen) await guest.close();
        }
    } finally {
        await stack.stop();
    }
});

test('a rate-limited stream reopens with bounded backoff', async ({ page, browser, useStack }) => {
    const stack = await useStack(
        'recovery-stream-limit',
        { frontendPort: 4175, backendPort: 8082 },
        { BATTLESHIP_RATELIMIT_STREAMOPENPERMINUTE: '2' },
    );
    try {
        const { guest } = await prepareGame(page, browser, stack.origin, 'Sea Battle');
        const streams: number[] = [];
        const streamResponses: Response[] = [];
        page.on('request', (request) => {
            if (new URL(request.url()).pathname.endsWith('/events')) streams.push(Date.now());
        });
        page.on('response', (response) => {
            if (new URL(response.url()).pathname.endsWith('/events')) streamResponses.push(response);
        });
        try {
            await toggleOffline(page, true);
            await expect(page.getByText('Offline', { exact: true })).toBeVisible();
            await toggleOffline(page, false);
            await expect.poll(() => streams.length, { timeout: 5000 }).toBe(1);
            await expect.poll(() => streamResponses.length, { timeout: 5000 }).toBe(1);
            expect(streamResponses[0].status()).toBe(200);
            await toggleOffline(page, true);
            await expect(page.getByText('Offline', { exact: true })).toBeVisible();
            await toggleOffline(page, false);
            await expect.poll(() => streams.length, { timeout: 5000 }).toBe(2);
            await expect.poll(() => streamResponses.length, { timeout: 5000 }).toBe(2);
            expect(streamResponses[1].status()).toBe(429);
            const afterLimitHit = streams.length;
            await page.waitForTimeout(10_000);
            expect(streams.length - afterLimitHit).toBeLessThanOrEqual(4);
        } finally {
            await guest.close();
        }
    } finally {
        await stack.stop();
    }
});
