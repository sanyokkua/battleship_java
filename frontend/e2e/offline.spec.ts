import { execFileSync } from 'node:child_process';
import { expect, test } from './fixtures/stack';
import { prepareGame } from './helpers/playGame';

async function createWaitingGame(page: import('@playwright/test').Page, origin: string) {
    await page.goto(`${origin}/create`);
    await page.getByRole('radio').filter({ hasText: 'Sea Battle' }).click();
    await page.getByLabel('Captain name').fill('Offline route');
    const created = page.waitForResponse(
        (response) => response.request().method() === 'POST' && response.url().endsWith('/api/v1/games'),
    );
    await page.getByRole('button', { name: 'Create game', exact: true }).click();
    const snapshot = (await (await created).json()) as { gameId: string };
    await expect(page.getByRole('heading', { name: 'Waiting for an opponent' })).toBeVisible();
    return snapshot;
}

test('an installed shell opens offline and explains that games need a connection without sending requests', async ({
    page,
    stack,
}) => {
    await page.goto(stack.origin);
    await page.evaluate(() => navigator.serviceWorker.ready);
    const manifestResponse = await page.request.get(`${stack.origin}/manifest.webmanifest`);
    const manifest = JSON.parse(await manifestResponse.text()) as {
        start_url: string;
        scope: string;
        display: string;
        icons: { src: string; sizes: string; type: string }[];
    };
    expect(manifest).toMatchObject({ start_url: '/', scope: '/', display: 'standalone' });
    expect(manifest.icons).toContainEqual(
        expect.objectContaining({ src: '/icons/battleship.svg', sizes: 'any', type: 'image/svg+xml' }),
    );

    const apiRequests: string[] = [];
    const createRequests: string[] = [];
    page.on('request', (request) => {
        const pathname = new URL(request.url()).pathname;
        if (pathname.startsWith('/api/')) apiRequests.push(request.url());
        if (request.method() === 'POST' && pathname === '/api/v1/games') createRequests.push(request.url());
    });
    await page.context().setOffline(true);
    await page.reload();

    await expect(page.getByRole('status').filter({ hasText: /games need a network connection/i })).toBeVisible({
        timeout: 3000,
    });
    await page.goto(`${stack.origin}/create`);
    await page.getByLabel('Captain name').fill('Offline player');
    await expect(page.getByRole('button', { name: 'Create game', exact: true })).toBeDisabled();
    expect(apiRequests).toEqual([]);
    await page.context().setOffline(false);
    await expect(page.getByRole('radio').first()).toBeVisible();
    expect(createRequests).toEqual([]);
});

test('service-worker caches contain no API responses after real game traffic', async ({ page, stack }) => {
    await page.goto(stack.origin);
    await page.evaluate(() => navigator.serviceWorker.ready);
    await page.reload();

    await page.goto(`${stack.origin}/create`);
    await page.getByRole('radio').filter({ hasText: 'Sea Battle' }).click();
    await page.getByLabel('Captain name').fill('Offline audit');
    const created = page.waitForResponse(
        (response) => response.request().method() === 'POST' && response.url().endsWith('/api/v1/games'),
    );
    const stream = page.waitForResponse((response) => response.url().endsWith('/events'));
    await page.getByRole('button', { name: 'Create game', exact: true }).click();
    const snapshot = JSON.parse(await (await created).text()) as { gameId: string };
    expect(snapshot.gameId).toBeTruthy();
    expect((await stream).ok()).toBe(true);
    await expect(page.getByRole('heading', { name: 'Waiting for an opponent' })).toBeVisible();

    const cachedUrls = await page.evaluate(async () => {
        const cacheNames = await caches.keys();
        const requests = await Promise.all(cacheNames.map(async (name) => (await caches.open(name)).keys()));
        return requests.flat().map((request) => new URL(request.url).pathname);
    });
    expect(cachedUrls.filter((path) => path.startsWith('/api/'))).toEqual([]);

    await page.context().setOffline(true);
    await expect(page.getByText('Offline', { exact: true })).toBeVisible();
    await expect(page.getByRole('status').filter({ hasText: /games need a network connection/i })).toBeVisible();
});

test('replacing an invitation cannot be confirmed after connectivity drops', async ({ page, stack }) => {
    await createWaitingGame(page, stack.origin);
    const invitationRequests: string[] = [];
    page.on('request', (request) => {
        if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/invitation')) {
            invitationRequests.push(request.url());
        }
    });
    await page.getByRole('button', { name: 'Replace invitation', exact: true }).click();
    const confirmation = page.getByRole('dialog', { name: 'Replace invitation?' });
    const beforeOffline = invitationRequests.length;
    await page.context().setOffline(true);
    await expect(confirmation.getByRole('button', { name: 'Replace link', exact: true })).toBeDisabled();
    await expect(page.getByRole('status').filter({ hasText: /games need a network connection/i })).toBeVisible();
    await confirmation.getByRole('button', { name: 'Replace link', exact: true }).click({ force: true });
    await expect(confirmation).toBeVisible();
    expect(invitationRequests).toHaveLength(beforeOffline);
});

test('leaving a waiting game cannot be confirmed after connectivity drops', async ({ page, stack }) => {
    await createWaitingGame(page, stack.origin);
    const leaveRequests: string[] = [];
    page.on('request', (request) => {
        if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/leave')) {
            leaveRequests.push(request.url());
        }
    });
    await page.getByRole('button', { name: 'Leave game', exact: true }).click();
    const confirmation = page.getByRole('dialog', { name: 'Leave game?' });
    const beforeOffline = leaveRequests.length;
    await page.context().setOffline(true);
    await expect(confirmation.getByRole('button', { name: 'Leave game', exact: true })).toBeDisabled();
    await confirmation.getByRole('button', { name: 'Leave game', exact: true }).click({ force: true });
    await expect(confirmation).toBeVisible();
    expect(leaveRequests).toHaveLength(beforeOffline);
});

test('a completed refresh cannot restore connected status after going offline', async ({ page, stack }) => {
    await page.goto(`${stack.origin}/create`);
    await page.getByRole('radio').filter({ hasText: 'Sea Battle' }).click();
    await page.getByLabel('Captain name').fill('Offline race');

    let releaseResponse!: () => void;
    let responseReady!: () => void;
    const responseHeld = new Promise<void>((resolve) => {
        responseReady = resolve;
    });
    const release = new Promise<void>((resolve) => {
        releaseResponse = resolve;
    });
    await page.route('**/api/v1/rulesets', async (route) => {
        responseReady();
        await release;
        await route.continue();
    });
    try {
        await page.getByRole('button', { name: 'Create game', exact: true }).click();
        await expect(page.getByRole('heading', { name: 'Waiting for an opponent' })).toBeVisible();
        await responseHeld;
        await page.context().setOffline(true);
        await expect(page.getByText('Offline', { exact: true })).toBeVisible();
        const refreshFailure = page.waitForEvent('requestfailed', (request) =>
            request.url().endsWith('/api/v1/rulesets'),
        );
        releaseResponse();
        await refreshFailure;
        await page.waitForTimeout(250);
        await expect(page.getByText('Offline', { exact: true })).toBeVisible();
    } finally {
        releaseResponse();
        await page.context().setOffline(false);
    }
});

test('a stale refresh cannot replace the recovered connection state', async ({ page, stack }) => {
    await page.goto(`${stack.origin}/create`);
    await page.getByRole('radio').filter({ hasText: 'Sea Battle' }).click();
    await page.getByLabel('Captain name').fill('Reconnect race');

    let releaseResponse!: () => void;
    let responseReady!: () => void;
    const responseHeld = new Promise<void>((resolve) => {
        responseReady = resolve;
    });
    const release = new Promise<void>((resolve) => {
        releaseResponse = resolve;
    });
    let first = true;
    await page.route('**/api/v1/rulesets', async (route) => {
        if (!first) return route.continue();
        first = false;
        responseReady();
        await release;
        await route.abort();
    });
    try {
        await page.getByRole('button', { name: 'Create game', exact: true }).click();
        await expect(page.getByRole('heading', { name: 'Waiting for an opponent' })).toBeVisible();
        await responseHeld;
        await page.context().setOffline(true);
        await expect(page.getByText('Offline', { exact: true })).toBeVisible();
        const recoveredRules = page.waitForResponse(
            (response) => response.url().endsWith('/api/v1/rulesets') && response.ok(),
        );
        await page.context().setOffline(false);
        await recoveredRules;
        await expect(page.getByText('Connected', { exact: true })).toBeVisible();
        await page.evaluate(() => {
            (window as unknown as { connectionChanges: string[] }).connectionChanges = [];
            const changes = (window as unknown as { connectionChanges: string[] }).connectionChanges;
            new MutationObserver(() => {
                if (document.body.textContent.includes('Reconnecting')) changes.push('Reconnecting');
            }).observe(document.body, { childList: true, subtree: true, characterData: true });
        });
        const oldFailure = page.waitForEvent('requestfailed', (request) => request.url().endsWith('/api/v1/rulesets'));
        releaseResponse();
        await oldFailure;
        await page.waitForTimeout(250);
        expect(
            await page.evaluate(() => (window as unknown as { connectionChanges: string[] }).connectionChanges),
        ).toEqual([]);
        await expect(page.getByText('Connected', { exact: true })).toBeVisible();
    } finally {
        releaseResponse();
        await page.context().setOffline(false);
    }
});

test('waiting-room expiry does not refresh offline and recovers on reconnect', async ({ page, useStack }) => {
    const shortStack = await useStack(
        'offline-invitation-expiry',
        { frontendPort: 4174, backendPort: 8081 },
        { BATTLESHIP_INVITATIONLIFETIMESECONDS: '10' },
    );
    try {
        const { gameId } = await createWaitingGame(page, shortStack.origin);
        const gameReads: number[] = [];
        page.on('request', (request) => {
            const url = new URL(request.url());
            if (request.method() === 'GET' && url.pathname === `/api/v1/games/${gameId}`) gameReads.push(Date.now());
        });
        await page.context().setOffline(true);
        await expect(page.getByText('Offline', { exact: true })).toBeVisible();
        await page.waitForTimeout(11_000);
        expect(gameReads).toEqual([]);
        await page.context().setOffline(false);
        await expect.poll(() => gameReads.length, { timeout: 5_000 }).toBeGreaterThan(0);
    } finally {
        await page.context().setOffline(false);
        await shortStack.stop();
    }
});

test('an offline deep link explains the network requirement and recovers after reconnecting', async ({
    page,
    stack,
}) => {
    await page.goto(stack.origin);
    await page.evaluate(() => navigator.serviceWorker.ready);
    const { gameId } = await createWaitingGame(page, stack.origin);

    const apiRequests: string[] = [];
    page.on('request', (request) => {
        if (new URL(request.url()).pathname.startsWith('/api/')) apiRequests.push(request.url());
    });
    await page.context().setOffline(true);
    await page.goto(`${stack.origin}/games/${gameId}`);

    await expect(page.getByRole('status').filter({ hasText: /games need a network connection/i })).toBeVisible({
        timeout: 3000,
    });
    expect(apiRequests).toEqual([]);

    await page.context().setOffline(false);
    await expect(page.getByRole('heading', { name: 'Waiting for an opponent' })).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText('Connected', { exact: true })).toBeVisible();
});

test('resignation cannot be confirmed or sent after connectivity drops', async ({ page, browser, stack }) => {
    const { guest } = await prepareGame(page, browser, stack.origin, 'Sea Battle');
    try {
        const commands: string[] = [];
        page.on('request', (request) => {
            if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/commands')) {
                commands.push(request.postData() ?? '');
            }
        });
        await page.getByRole('button', { name: 'Settings' }).click();
        await page.getByRole('button', { name: 'Resign match', exact: true }).click();
        const confirmation = page.getByRole('dialog', { name: 'Resign match?' });
        await expect(confirmation.getByRole('button', { name: 'Resign match', exact: true })).toBeEnabled();
        const requestsBeforeOffline = commands.length;

        await page.context().setOffline(true);
        await expect(confirmation.getByRole('button', { name: 'Resign match', exact: true })).toBeDisabled();
        await confirmation.getByRole('button', { name: 'Resign match', exact: true }).click({ force: true });
        await expect(confirmation).toBeVisible();
        expect(commands).toHaveLength(requestsBeforeOffline);
        await confirmation.getByRole('button', { name: 'Cancel' }).click();
        await expect(
            page.getByRole('dialog', { name: 'Settings' }).getByRole('button', { name: 'Resign match' }),
        ).toBeDisabled();
    } finally {
        await page.context().setOffline(false);
        await guest.close();
    }
});

test('a detected update waits for consent before reloading', async ({ page, stack }) => {
    await page.goto(stack.origin);
    await page.evaluate(() => navigator.serviceWorker.ready);
    await page.getByRole('button', { name: 'Settings' }).click();
    const settings = page.getByRole('dialog', { name: 'Settings' });
    const buildId = settings.getByText(/build/i);
    await expect(buildId).toBeVisible();
    const initial = await buildId.textContent();
    let navigations = 0;
    page.on('framenavigated', (frame) => {
        if (frame === page.mainFrame()) navigations += 1;
    });
    await settings.getByRole('button', { name: 'Done' }).click();

    execFileSync('npm', ['run', 'build'], {
        cwd: process.cwd(),
        env: { ...process.env, VITE_BUILD_ID: 'next' },
        stdio: 'pipe',
    });
    await page.reload();

    await expect(page.getByRole('status').filter({ hasText: /update is available/i })).toBeVisible({ timeout: 10_000 });
    const navigationsBeforeConsent = navigations;
    await page.waitForTimeout(500);
    expect(navigations).toBe(navigationsBeforeConsent);
    await page.getByRole('button', { name: 'Settings' }).click();
    await expect(page.getByRole('dialog', { name: 'Settings' }).getByText(initial ?? '')).toBeVisible();
    await page.getByRole('button', { name: 'Done' }).click();
    await page.getByRole('button', { name: /update now/i }).click();
    await expect(page.getByRole('button', { name: 'Settings' })).toBeVisible();
    await page.getByRole('button', { name: 'Settings' }).click();
    await expect(page.getByRole('dialog', { name: 'Settings' }).getByText('Build: next')).toBeVisible();
    expect(initial).not.toBe('Build: next');
});
