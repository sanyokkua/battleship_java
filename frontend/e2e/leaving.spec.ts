import { expect, test } from './fixtures/stack.js';
import { prepareGame, type Snapshot } from './helpers/playGame.js';

async function preparePlacement(
    page: import('@playwright/test').Page,
    browser: import('@playwright/test').Browser,
    origin: string,
) {
    await page.goto(`${origin}/create`);
    await page.getByRole('radio').filter({ hasText: 'Sea Battle' }).click();
    await page.getByLabel('Captain name').fill('Host');
    const created = page.waitForResponse((response) => response.url().endsWith('/api/v1/games'));
    await page.getByRole('button', { name: 'Create game', exact: true }).click();
    const snapshot = (await (await created).json()) as Snapshot;
    if (!snapshot.invitationUrl) throw new Error('Invitation missing');
    const guest = await browser.newPage();
    await guest.goto(`${origin}/join/${snapshot.gameId}${new URL(snapshot.invitationUrl).hash}`);
    await guest.getByLabel('Captain name').fill('Guest');
    await guest.getByRole('button', { name: 'Join game', exact: true }).click();
    await expect(page.getByRole('grid', { name: 'Your sea' })).toBeVisible();
    return { guest, gameId: snapshot.gameId };
}

test('leaving placement can be canceled or confirmed and the opponent sees abandonment', async ({
    page,
    browser,
    stack,
}) => {
    const { guest } = await preparePlacement(page, browser, stack.origin);
    try {
        await page.getByRole('button', { name: 'Leave game', exact: true }).click();
        await expect(page.getByRole('dialog', { name: 'Leave game?' })).toBeVisible();
        await page.getByRole('button', { name: 'Cancel', exact: true }).click();
        await expect(page.getByRole('grid', { name: 'Your sea' })).toBeVisible();
        await page.getByRole('button', { name: 'Leave game', exact: true }).click();
        const left = page.waitForResponse((response) => response.url().endsWith('/leave'));
        await page.getByRole('button', { name: 'Leave game', exact: true }).last().click();
        expect((await left).status()).toBe(204);
        await expect(page.getByRole('heading', { name: 'Naval command center', exact: true })).toBeVisible();
        await expect(guest.getByRole('heading', { name: 'Game abandoned', exact: true })).toBeVisible();
        await expect(guest.getByRole('grid')).toHaveCount(0);
        await expect(guest.getByText('Statistics', { exact: true })).toHaveCount(0);
    } finally {
        await guest.close();
    }
});

test('resign is offered only in play and the opponent receives a resignation win', async ({ page, browser, stack }) => {
    await page.goto(stack.origin);
    await page.getByRole('button', { name: 'Settings' }).click();
    await expect(page.getByRole('button', { name: 'Resign match', exact: true })).toHaveCount(0);
    await page.getByRole('button', { name: 'Done', exact: true }).click();
    const { guest } = await prepareGame(page, browser, stack.origin, 'Sea Battle');
    try {
        await page.getByRole('button', { name: 'Settings' }).click();
        await expect(page.getByRole('button', { name: 'Resign match', exact: true })).toBeVisible();
        await page.getByRole('button', { name: 'Resign match', exact: true }).click();
        await expect(page.getByRole('dialog', { name: 'Resign match?' })).toBeVisible();
        const command = page.waitForResponse((response) => response.url().endsWith('/commands'));
        await page.getByRole('button', { name: 'Resign match', exact: true }).last().click();
        expect((await command).ok()).toBeTruthy();
        await expect(guest.getByRole('heading', { name: 'Victory', exact: true })).toBeVisible();
        await expect(guest.getByText('Match ended by resignation', { exact: true })).toBeVisible();
    } finally {
        await guest.close();
    }
});

test('a lost resignation answer can be retried with the same command ID', async ({ page, browser, stack }) => {
    const { guest } = await prepareGame(page, browser, stack.origin, 'Sea Battle');
    const requests: { commandId: string; command: { type: string } }[] = [];
    let loseNextAnswer = true;
    try {
        await page.route('**/api/v1/games/*/commands', async (route) => {
            const request = route.request();
            if (request.method() !== 'POST') {
                await route.continue();
                return;
            }
            const body = request.postDataJSON() as { commandId: string; command: { type: string } };
            requests.push(body);
            if (loseNextAnswer) {
                loseNextAnswer = false;
                await route.fetch();
                await route.abort();
            } else await route.continue();
        });
        await page.getByRole('button', { name: 'Settings' }).click();
        await page.getByRole('button', { name: 'Resign match', exact: true }).click();
        await page.getByRole('button', { name: 'Resign match', exact: true }).last().click();
        await expect(page.getByRole('dialog', { name: 'Checking shot' })).toBeVisible();
        const retried = page.waitForResponse((response) => response.url().endsWith('/commands'));
        await page.getByRole('button', { name: 'Retry', exact: true }).click();
        expect((await retried).ok()).toBeTruthy();
        expect(requests).toHaveLength(2);
        expect(requests[0]).toEqual(requests[1]);
        expect(requests[0]?.command).toEqual({ type: 'RESIGN' });
        await expect(guest.getByRole('heading', { name: 'Victory', exact: true })).toBeVisible();
    } finally {
        await guest.close();
    }
});

test('a lost leave answer can be retried and 404 returns home', async ({ page, stack }) => {
    let lost = false;
    await page.route('**/api/v1/games/*/leave', async (route) => {
        if (!lost) {
            lost = true;
            await route.fetch();
            await route.abort();
        } else await route.continue();
    });
    await page.goto(`${stack.origin}/create`);
    await page.getByRole('radio').filter({ hasText: 'Sea Battle' }).click();
    await page.getByLabel('Captain name').fill('Host');
    await page.getByRole('button', { name: 'Create game', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Waiting for an opponent' })).toBeVisible();
    await page.getByRole('button', { name: 'Leave game', exact: true }).click();
    await page.getByRole('button', { name: 'Leave game', exact: true }).last().click();
    await expect(page.getByRole('alert')).toBeVisible();
    const retry = page.waitForResponse((response) => response.url().endsWith('/leave'));
    await page.getByRole('button', { name: 'Retry leave', exact: true }).click();
    expect((await retry).status()).toBe(404);
    await expect(page.getByRole('heading', { name: 'Naval command center', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: /game (expired|ended)/i })).toHaveCount(0);
});
