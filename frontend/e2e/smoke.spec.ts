import { readFile } from 'node:fs/promises';
import { type APIResponse, type Page } from '@playwright/test';
import { expect, test } from './fixtures/stack.js';
import { prepareGame } from './helpers/playGame.js';

async function createGame(page: Page, origin: string) {
    const meta = await page.request.get(`${origin}/api/v1/meta`);
    expect(meta.ok()).toBeTruthy();
    const csrf = (await page.context().cookies(origin)).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
    expect(csrf).toBeTruthy();
    const response = await page.request.post(`${origin}/api/v1/games`, {
        headers: { Cookie: `XSRF-TOKEN=${csrf ?? ''}`, 'X-XSRF-TOKEN': csrf ?? '', Origin: origin },
        data: { rulesetId: 'sea-battle-10-ship.v1', displayName: 'Smoke test' },
    });
    expect(response.status()).toBe(201);
    return readJson<{ gameId: string }>(response);
}

async function readJson<T>(response: APIResponse): Promise<T> {
    const body: unknown = await response.json();
    return body as T;
}

async function firstSnapshot(page: Page, gameId: string) {
    return page.evaluate<{ phase: string }, { id: string }>(
        ({ id }) =>
            new Promise<{ phase: string }>((resolve, reject) => {
                const stream = new window.EventSource(`/api/v1/games/${id}/events`);
                const timeout = window.setTimeout(() => {
                    stream.close();
                    reject(new Error('snapshot event timed out'));
                }, 10_000);
                stream.addEventListener('snapshot', (event) => {
                    window.clearTimeout(timeout);
                    stream.close();
                    const data: unknown = JSON.parse((event as MessageEvent<string>).data);
                    resolve(data as { phase: string });
                });
                stream.onerror = () => {
                    window.clearTimeout(timeout);
                    stream.close();
                    reject(new Error('event stream failed'));
                };
            }),
        { id: gameId },
    );
}

test('preview serves the shell and proxies health, game snapshots, and deep links', async ({ page, stack }) => {
    await page.goto(stack.origin);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    const health = await page.request.get(`${stack.origin}/api/v1/health`);
    expect(health.status()).toBe(200);
    expect(await readJson<{ live: boolean; ready: boolean }>(health)).toMatchObject({ live: true, ready: true });

    const game = await createGame(page, stack.origin);
    const snapshot = await firstSnapshot(page, game.gameId);
    expect(snapshot.phase).toBe('WAITING');

    for (const path of ['/games/abc', '/join/abc']) {
        const response = await page.request.get(`${stack.origin}${path}`);
        expect(response.status()).toBe(200);
        expect(await response.text()).toContain('<div id="root"></div>');
    }

    const problemResponse = await page.request.get(`${stack.origin}/api/v1/games/not-a-game`);
    const problem = await readJson<{ correlationId: string }>(problemResponse);
    expect(problemResponse.status()).toBe(404);
    expect(problem.correlationId).toMatch(/^[0-9a-f]{16}$/);
    await expect.poll(async () => readFile(stack.logPath, 'utf8')).toContain(`correlationId=${problem.correlationId}`);
});

test('the shell carries the security headers and the app runs without a CSP violation', async ({
    page,
    browser,
    stack,
}) => {
    const violations: string[] = [];
    page.on('console', (message) => {
        if (/content security policy/i.test(message.text())) violations.push(message.text());
    });

    const response = await page.goto(stack.origin);
    const headers = response?.headers() ?? {};
    expect(headers['content-security-policy']).toContain("default-src 'self'");
    expect(headers['content-security-policy']).toContain("frame-ancestors 'none'");
    expect(headers['referrer-policy']).toBe('no-referrer');
    expect(headers['x-content-type-options']).toBe('nosniff');
    expect(headers['x-frame-options']).toBe('DENY');
    expect(headers['cache-control']).toBe('no-cache');
    const worker = await page.request.get(`${stack.origin}/sw.js`);
    expect(worker.headers()['cache-control']).toBe('no-cache');
    expect(worker.headers()['x-frame-options']).toBe('DENY');

    // Creating, joining and placing a fleet covers inline style attributes, the canvas QR code and the event stream.
    const { guest } = await prepareGame(page, browser, stack.origin, 'Sea Battle');
    try {
        expect(violations).toEqual([]);
    } finally {
        await guest.close();
    }
});
