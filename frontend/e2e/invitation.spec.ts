import { expect, test } from './fixtures/stack.js';
import type { components } from '../src/contract/generated/api';
import { readFile } from 'node:fs/promises';

type Snapshot = components['schemas']['GameSnapshot'];

async function invitation(page: import('@playwright/test').Page, origin: string, displayName = 'Host') {
    await page.goto(`${origin}/create`);
    await page.getByLabel('Captain name').fill(displayName);
    const responsePromise = page.waitForResponse(
        (response) => response.request().method() === 'POST' && response.url().endsWith('/api/v1/games'),
    );
    await page.getByRole('button', { name: 'Create game' }).click();
    const response = await responsePromise;
    expect(response.ok()).toBeTruthy();
    return (await response.json()) as Snapshot;
}

test('invitation is stripped before render, redeemed only on intent, and never retained', async ({
    page,
    browser,
    stack,
}) => {
    const host = await invitation(page, stack.origin);
    const secret = host.invitationUrl?.split('#invite=')[1];
    if (!secret) throw new Error('Host response did not contain an invitation secret.');
    const guest = await browser.newPage();
    await guest.addInitScript(() => {
        const observer = new MutationObserver(() => {
            const root = document.getElementById('root');
            if (root && root.childNodes.length > 0) {
                (window as typeof window & { firstRenderHash?: string }).firstRenderHash = location.hash;
                observer.disconnect();
            }
        });
        observer.observe(document, { childList: true, subtree: true });
    });
    const joinRequests: string[] = [];
    guest.on('request', (request) => {
        if (request.method() === 'POST' && request.url().endsWith('/join')) joinRequests.push(request.url());
    });
    const consoleMessages: string[] = [];
    guest.on('console', (message) => consoleMessages.push(message.text()));
    await guest.goto(`${stack.origin}/join/${host.gameId}#invite=${secret}`);
    await expect(guest).toHaveURL(new RegExp(`/join/${host.gameId}(#.*)?$`));
    await guest.evaluate(() => {
        window.history.pushState({}, '', '/');
        window.dispatchEvent(new PopStateEvent('popstate'));
    });
    await expect(guest).toHaveURL(`${stack.origin}/`);
    await guest.goBack();
    await expect(guest).toHaveURL(new RegExp(`/join/${host.gameId}(#.*)?$`));
    expect(new URL(guest.url()).pathname).toBe(`/join/${host.gameId}`);
    expect(new URL(guest.url()).hash).toBe('');
    expect(joinRequests).toHaveLength(0);
    await expect
        .poll(() => guest.evaluate(() => (window as typeof window & { firstRenderHash?: string }).firstRenderHash))
        .toBe('');
    expect(joinRequests).toHaveLength(0);
    await guest.getByLabel('Captain name').fill('Guest');
    await guest.getByRole('button', { name: 'Join game' }).click();
    await expect(guest).toHaveURL(new RegExp(`/games/${host.gameId}$`));
    expect(joinRequests).toHaveLength(1);
    expect(await guest.evaluate(() => localStorage.getItem('battleship.currentGameId'))).toBe(host.gameId);
    expect(await guest.evaluate(() => JSON.stringify(localStorage))).not.toContain(secret);
    expect(await guest.evaluate(() => JSON.stringify(sessionStorage))).not.toContain(secret);
    expect(consoleMessages.join('\n')).not.toContain(secret);
    expect(await readFile('e2e/.logs/default.log', 'utf8')).not.toContain(secret);
    await expect(page.getByRole('heading', { name: 'Place your fleet' })).toBeVisible();
    await guest.close();
});

test('a lost join answer is not retried and becomes the unavailable outcome', async ({ browser, stack }) => {
    const hostPage = await browser.newPage();
    const host = await invitation(hostPage, stack.origin);
    const secret = host.invitationUrl?.split('#invite=')[1];
    if (!secret) throw new Error('Host response did not contain an invitation secret.');
    const guest = await browser.newPage();
    await guest.goto(`${stack.origin}/join/${host.gameId}#invite=${secret}`);
    let requests = 0;
    await guest.route('**/join', async (route) => {
        requests += 1;
        await route.fetch();
        await route.abort();
    });
    await guest.getByLabel('Captain name').fill('Guest');
    await guest.getByRole('button', { name: 'Join game' }).click();
    await expect(guest.getByText('This invitation is unavailable.')).toBeVisible();
    expect(requests).toBe(1);
    await hostPage.close();
    await guest.close();
});

test('request security rejection refreshes meta and retries the deliberate join once', async ({ browser, stack }) => {
    const hostPage = await browser.newPage();
    const host = await invitation(hostPage, stack.origin);
    const secret = host.invitationUrl?.split('#invite=')[1];
    if (!secret) throw new Error('Host response did not contain an invitation secret.');
    const guest = await browser.newPage();
    await guest.goto(`${stack.origin}/join/${host.gameId}#invite=${secret}`);
    await guest.getByLabel('Captain name').fill('Guest');
    let joins = 0;
    await guest.route('**/join', async (route) => {
        joins += 1;
        if (joins === 1) {
            await route.continue({
                headers: { ...route.request().headers(), 'X-XSRF-TOKEN': 'invalid-token' },
            });
            return;
        }
        await route.continue();
    });
    await guest.getByRole('button', { name: 'Join game' }).click();
    await expect(guest).toHaveURL(new RegExp(`/games/${host.gameId}$`));
    expect(joins).toBe(2);
    await hostPage.close();
    await guest.close();
});

test('two guests race for one invitation and only one joins', async ({ page, browser, stack }) => {
    const host = await invitation(page, stack.origin);
    const secret = host.invitationUrl?.split('#invite=')[1];
    if (!secret) throw new Error('Host response did not contain an invitation secret.');
    const guests = await Promise.all([browser.newPage(), browser.newPage()]);
    await Promise.all(
        guests.map(async (guest, index) => {
            await guest.goto(`${stack.origin}/join/${host.gameId}#invite=${secret}`);
            await guest.getByLabel('Captain name').fill(`Guest ${String(index + 1)}`);
        }),
    );
    await Promise.all(guests.map((guest) => guest.getByRole('button', { name: 'Join game' }).click()));
    await Promise.all(
        guests.map(async (guest) => {
            await expect
                .poll(
                    async () =>
                        guest.url().includes('/games/') ||
                        (await guest.getByRole('heading', { name: 'This invitation is unavailable.' }).isVisible()),
                )
                .toBeTruthy();
        }),
    );
    const outcomes = await Promise.all(
        guests.map(async (guest) => {
            await expect(guest).toHaveURL(new RegExp(`(/games/${host.gameId}|/join/${host.gameId})$`));
            return guest.url().includes('/games/') ? 'joined' : 'unavailable';
        }),
    );
    expect([...outcomes].sort()).toEqual(['joined', 'unavailable']);
    const joinedGuest = guests[outcomes.indexOf('joined')];
    const unavailableGuest = guests[outcomes.indexOf('unavailable')];
    await expect(unavailableGuest.locator('body')).toContainText('This invitation is unavailable.');
    await expect(joinedGuest.locator('body')).toContainText('Place your fleet');
    await expect(page.getByRole('heading', { name: 'Place your fleet' })).toBeVisible();
    await Promise.all(guests.map((guest) => guest.close()));
});

test('browser game limit is distinct and returns to the remembered game without retry', async ({ browser, stack }) => {
    const context = await browser.newContext();
    const page = await context.newPage();
    const current = await invitation(page, stack.origin, 'Current Host');
    const targetPage = await browser.newPage();
    const target = await invitation(targetPage, stack.origin, 'Other Host');
    const secret = target.invitationUrl?.split('#invite=')[1];
    if (!secret) throw new Error('Host response did not contain an invitation secret.');
    await page.goto(`${stack.origin}/join/${target.gameId}#invite=${secret}`);
    const requests: string[] = [];
    page.on('request', (request) => {
        if (request.method() === 'POST' && request.url().endsWith('/join')) requests.push(request.url());
    });
    await page.getByLabel('Captain name').fill('Guest');
    await page.getByRole('button', { name: 'Join game' }).click();
    await expect(page.getByRole('alert')).toContainText('You already have a game in this browser.');
    await expect(page.getByRole('button', { name: 'Return to your game' })).toBeVisible();
    expect(requests).toHaveLength(1);
    await page.getByRole('button', { name: 'Return to your game' }).click();
    await expect(page).toHaveURL(new RegExp(`/games/${current.gameId}$`));
    await targetPage.close();
    await context.close();
});

test('an expired game invitation shows the expired session outcome', async ({ browser, useStack }) => {
    const shortStack = await useStack(
        'invitation-expired',
        { frontendPort: 4174, backendPort: 8081 },
        {
            BATTLESHIP_IDLETIMEOUTSECONDS: '8',
            BATTLESHIP_SWEEPINTERVALSECONDS: '1',
            BATTLESHIP_RESULTRETENTIONSECONDS: '60',
        },
    );
    try {
        const hostPage = await browser.newPage();
        try {
            const guest = await browser.newPage();
            try {
                const host = await invitation(hostPage, shortStack.origin);
                const secret = host.invitationUrl?.split('#invite=')[1];
                if (!secret) throw new Error('Host response did not contain an invitation secret.');
                await guest.goto(`${shortStack.origin}/join/${host.gameId}#invite=${secret}`);
                await guest.getByLabel('Captain name').fill('Guest');
                await guest.getByRole('button', { name: 'Join game' }).click();
                await expect(guest).toHaveURL(new RegExp(`/games/${host.gameId}$`));
                await Promise.all(
                    [hostPage, guest].map((hiddenPage) =>
                        hiddenPage.evaluate(() => {
                            Object.defineProperty(document, 'visibilityState', {
                                configurable: true,
                                get: () => 'hidden',
                            });
                        }),
                    ),
                );
                await new Promise((resolve) => setTimeout(resolve, 10000));
                await guest.goto(`${shortStack.origin}/join/${host.gameId}#invite=${secret}`);
                await guest.getByLabel('Captain name').fill('Guest');
                await guest.getByRole('button', { name: 'Join game' }).click();
                await expect(guest.getByRole('heading', { name: 'This game has expired.' })).toBeVisible();
                expect(await guest.evaluate(() => localStorage.getItem('battleship.currentGameId'))).toBeNull();
            } finally {
                await guest.close();
            }
        } finally {
            await hostPage.close();
        }
    } finally {
        await shortStack.stop();
    }
});
