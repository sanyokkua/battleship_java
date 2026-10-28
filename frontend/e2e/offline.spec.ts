import { execFileSync } from 'node:child_process';
import { expect, test } from './fixtures/stack';

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
    await expect(page.getByRole('status').filter({ hasText: /games need a network connection/i })).toHaveCount(0);
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
