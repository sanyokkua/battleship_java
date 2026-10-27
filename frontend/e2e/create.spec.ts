import { expect, test } from './fixtures/stack.js';
import { translate } from '../src/i18n/translate';
import type { components } from '../src/contract/generated/api';

type Ruleset = components['schemas']['Ruleset'];

function advertisedFacts(ruleset: Ruleset): { label: string; value: string }[] {
    const fleet = ruleset.fleet
        .map((entry) => {
            const ship = entry.shipTypeId.startsWith('ship-')
                ? `${String(entry.length)} ${translate('en', 'rules.cellShip')}`
                : translate('en', `ship.${entry.shipTypeId}`);
            return `${String(entry.count)} × ${ship} (${String(entry.length)})`;
        })
        .join(', ');
    return [
        {
            label: translate('en', 'rules.field'),
            value: `${String(ruleset.board.columns)} × ${String(ruleset.board.rows)}`,
        },
        { label: translate('en', 'rules.fleet'), value: fleet },
        {
            label: translate('en', 'rules.contact'),
            value: ruleset.shipsMayTouch ? translate('en', 'common.yes') : translate('en', 'common.no'),
        },
        {
            label: translate('en', 'rules.extraTurn'),
            value: ruleset.extraTurnOnHit ? translate('en', 'common.yes') : translate('en', 'common.no'),
        },
        {
            label: translate('en', 'rules.revealedWater'),
            value: ruleset.revealWaterAroundSunk ? translate('en', 'common.yes') : translate('en', 'common.no'),
        },
    ];
}

async function expectDialogFacts(page: import('@playwright/test').Page, rulesets: Ruleset[]) {
    const dialog = page.getByRole('dialog', { name: 'Game rules' });
    await expect(dialog).toBeVisible();
    const sections = dialog.locator('section');
    await expect(sections).toHaveCount(rulesets.length);
    for (const [index, ruleset] of rulesets.entries()) {
        const facts = advertisedFacts(ruleset);
        expect(await sections.nth(index).locator('dt').allTextContents()).toEqual(facts.map((fact) => fact.label));
        expect(await sections.nth(index).locator('dd').allTextContents()).toEqual(facts.map((fact) => fact.value));
    }
}

test('a host can review server rules and create a game exactly once', async ({ page, stack }) => {
    const posts: string[] = [];
    let releaseResponse: (() => void) | undefined;
    const responseGate = new Promise<void>((resolve) => {
        releaseResponse = resolve;
    });
    await page.route('**/api/v1/games', async (route) => {
        if (route.request().method() !== 'POST') {
            await route.continue();
            return;
        }
        await responseGate;
        await route.continue();
    });
    page.on('request', (request) => {
        if (request.method() === 'POST' && request.url().endsWith('/api/v1/games')) posts.push(request.url());
    });
    await page.goto(stack.origin);
    await page.getByRole('button', { name: 'New game' }).click();
    await expect(page.getByRole('heading', { name: 'Create game' })).toBeVisible();
    await page.getByLabel('Captain name').fill('Test Captain');
    const submit = page.getByRole('button', { name: 'Create game' });
    await submit.dblclick();
    await expect.poll(() => posts.length).toBe(1);
    await expect(submit).toBeDisabled();
    releaseResponse?.();
    await expect(page).toHaveURL(/\/games\//);
    await expect.poll(() => posts.length).toBe(1);
    await expect(page.getByText('Waiting for an opponent')).toBeVisible();
    expect(await page.evaluate(() => (window.history.state as { toast?: unknown } | null)?.toast)).toBe('game-created');
    await expect(page.getByText('Game created.')).toHaveCount(0);
    expect(await page.locator('html').evaluate((element) => element.outerHTML)).not.toContain('#invite=');
});

test('the server-backed game rules dialog opens from Home', async ({ page, stack }) => {
    const response = await page.request.get(`${stack.origin}/api/v1/rulesets`);
    expect(response.ok()).toBeTruthy();
    const body = (await response.json()) as components['schemas']['RulesetList'];
    await page.goto(stack.origin);
    await page.getByRole('main').getByRole('button', { name: 'Game rules' }).click();
    await expectDialogFacts(page, body.rulesets);
});

test('rules cards and details use the advertised ruleset facts', async ({ page, stack }) => {
    const response = await page.request.get(`${stack.origin}/api/v1/rulesets`);
    expect(response.ok()).toBeTruthy();
    const body = (await response.json()) as components['schemas']['RulesetList'];
    await page.goto(`${stack.origin}/create`);
    const cards = page.getByRole('radio');
    await expect(cards).toHaveCount(body.rulesets.length);
    for (const [index, ruleset] of body.rulesets.entries()) {
        const facts = advertisedFacts(ruleset);
        expect(await cards.nth(index).locator('dt').allTextContents()).toEqual(facts.map((fact) => fact.label));
        expect(await cards.nth(index).locator('dd').allTextContents()).toEqual(facts.map((fact) => fact.value));
    }
    await page.getByRole('button', { name: 'View details' }).click();
    await expectDialogFacts(page, body.rulesets);
});

test('create stays disabled until the server advertises a selectable ruleset', async ({ page, stack }) => {
    let releaseRulesets: (() => void) | undefined;
    const rulesetGate = new Promise<void>((resolve) => {
        releaseRulesets = resolve;
    });
    await page.route('**/api/v1/rulesets', async (route) => {
        await rulesetGate;
        await route.continue();
    });
    await page.goto(`${stack.origin}/create`);
    await page.getByLabel('Captain name').fill('Early Host');
    const submit = page.getByRole('button', { name: 'Create game' });
    await expect(submit).toBeDisabled();
    releaseRulesets?.();
    await expect(submit).toBeEnabled();
});

test('the global rules action shows server rules from a real game route', async ({ page, stack }) => {
    const response = await page.request.get(`${stack.origin}/api/v1/rulesets`);
    expect(response.ok()).toBeTruthy();
    const body = (await response.json()) as components['schemas']['RulesetList'];
    await page.goto(`${stack.origin}/create`);
    await page.getByLabel('Captain name').fill('Rules Host');
    await page.getByRole('button', { name: 'Create game' }).click();
    await expect(page).toHaveURL(/\/games\//);
    await page.getByRole('button', { name: 'Game rules' }).click();
    await expectDialogFacts(page, body.rulesets);
});

test('create success survives reload and browser tab reopening', async ({ page, stack }) => {
    await page.goto(`${stack.origin}/create`);
    await page.getByLabel('Captain name').fill('Reload Host');
    await page.getByRole('button', { name: 'Create game' }).click();
    await expect(page).toHaveURL(/\/games\//);
    await expect(page.getByText('Waiting for an opponent')).toBeVisible();
    await page.reload();
    await expect(page.getByText('Waiting for an opponent')).toBeVisible();
    const reopened = await page.context().newPage();
    await reopened.goto(`${stack.origin}/`);
    await expect(reopened.getByRole('button', { name: 'Return to your game' })).toBeVisible();
    await reopened.close();
});

test('a deliberate second create is refused without an automatic retry', async ({ page, stack }) => {
    let creates = 0;
    page.on('request', (request) => {
        if (request.method() === 'POST' && request.url().endsWith('/api/v1/games')) creates++;
    });
    await page.goto(`${stack.origin}/create`);
    await page.getByLabel('Captain name').fill('First Host');
    await page.getByRole('button', { name: 'Create game' }).click();
    await expect(page).toHaveURL(/\/games\//);
    await page.evaluate(() => {
        localStorage.clear();
    });
    await page.goto(`${stack.origin}/create`);
    await page.getByLabel('Captain name').fill('Second Host');
    await page.getByRole('button', { name: 'Create game' }).click();
    await expect(page.getByRole('alert')).toContainText('already have a game');
    expect(creates).toBe(2);
});

test('a lost create answer is shown as unknown and never resent', async ({ page, stack }) => {
    let creates = 0;
    page.on('request', (request) => {
        if (request.method() === 'POST' && request.url().endsWith('/api/v1/games')) creates++;
    });
    await page.route('**/api/v1/games', async (route) => {
        if (route.request().method() !== 'POST') return route.continue();
        await route.fetch();
        await route.abort();
    });
    await page.goto(`${stack.origin}/create`);
    await page.getByLabel('Captain name').fill('Unknown Host');
    await page.getByRole('button', { name: 'Create game' }).click();
    await expect(page.getByRole('alert')).toContainText('response was lost');
    expect(creates).toBe(1);
});

test('refresh performs one current-snapshot request', async ({ page, stack }) => {
    await page.goto(`${stack.origin}/create`);
    await page.getByLabel('Captain name').fill('Refresh Host');
    await page.getByRole('button', { name: 'Create game' }).click();
    await expect(page).toHaveURL(/\/games\//);
    let gets = 0;
    page.on('request', (request) => {
        if (request.method() === 'GET' && /\/api\/v1\/games\/[^/]+$/.test(request.url())) gets++;
    });
    await page.getByRole('banner').getByRole('button', { name: 'Refresh' }).click();
    await expect.poll(() => gets).toBe(1);
});

test('a rejected CSRF token refreshes meta and resends once', async ({ page, context, stack }) => {
    const counts = { meta: 0, posts: 0, rejected: 0 };
    page.on('request', (request) => {
        if (request.url().endsWith('/api/v1/meta') && request.method() === 'GET') counts.meta++;
        if (request.url().endsWith('/api/v1/games') && request.method() === 'POST') counts.posts++;
    });
    page.on('response', (response) => {
        if (
            response.url().endsWith('/api/v1/games') &&
            response.request().method() === 'POST' &&
            response.status() === 403
        )
            counts.rejected++;
    });
    await page.goto(`${stack.origin}/create`);
    await expect.poll(() => counts.meta).toBe(1);
    await context.clearCookies({ name: 'XSRF-TOKEN' });
    await page.getByLabel('Captain name').fill('CSRF Host');
    await page.getByRole('button', { name: 'Create game' }).click();
    await expect(page).toHaveURL(/\/games\//);
    expect(counts).toEqual({ meta: 2, posts: 2, rejected: 1 });
});

test('a real 429 displays its wait and blocks another create', async ({ browser, useStack }) => {
    const stack = await useStack(
        'create-limit',
        { frontendPort: 4175, backendPort: 8082 },
        { BATTLESHIP_RATELIMIT_CREATEGAMEPERMINUTE: '1' },
    );
    try {
        const first = await browser.newPage();
        await first.goto(`${stack.origin}/create`);
        await first.getByLabel('Captain name').fill('Limited One');
        await first.getByRole('button', { name: 'Create game' }).click();
        await expect(first).toHaveURL(/\/games\//);
        const second = await browser.newPage();
        let posts = 0;
        second.on('request', (request) => {
            if (request.method() === 'POST' && request.url().endsWith('/api/v1/games')) posts++;
        });
        await second.goto(`${stack.origin}/create`);
        await second.getByLabel('Captain name').fill('Limited Two');
        await second.getByRole('button', { name: 'Create game' }).click();
        await expect(second.getByRole('alert')).toContainText('Too many requests');
        await expect(second.getByRole('alert')).toContainText(/\b\d+ seconds?\b/);
        await expect(second.getByRole('button', { name: 'Create game' })).toBeDisabled();
        expect(posts).toBe(1);
        await first.close();
        await second.close();
    } finally {
        await stack.stop();
    }
});

test('GameRoute still retrieves a game when metadata and ruleset reads fail', async ({ page, stack }) => {
    await page.goto(`${stack.origin}/create`);
    await page.getByLabel('Captain name').fill('Read Failure Host');
    await page.getByRole('button', { name: 'Create game' }).click();
    await expect(page).toHaveURL(/\/games\//);
    const route = new URL(page.url());
    let gameGets = 0;
    await page.route('**/api/v1/meta', (requestRoute) => requestRoute.abort());
    await page.route('**/api/v1/rulesets', (requestRoute) => requestRoute.abort());
    page.on('request', (request) => {
        if (request.method() === 'GET' && /\/api\/v1\/games\/[^/]+$/.test(request.url())) gameGets++;
    });
    await page.goto(route.toString());
    await expect(page.getByText('Waiting for an opponent')).toBeVisible();
    expect(gameGets).toBeGreaterThan(0);
});

test('a game transport failure is visible while a previously loaded snapshot remains', async ({ page, stack }) => {
    await page.goto(`${stack.origin}/create`);
    await page.getByLabel('Captain name').fill('Offline Host');
    await page.getByRole('button', { name: 'Create game' }).click();
    await expect(page).toHaveURL(/\/games\//);
    await expect(page.getByText('Waiting for an opponent')).toBeVisible();
    expect(await page.evaluate(() => navigator.onLine)).toBe(true);
    await page.route('**/api/v1/games/*', async (route) => {
        if (route.request().method() === 'GET' && /\/api\/v1\/games\/[^/]+$/.test(route.request().url())) {
            await route.fetch();
            await route.abort();
            return;
        }
        await route.continue();
    });
    await page.getByRole('banner').getByRole('button', { name: 'Refresh' }).click();
    await expect(page.getByText('Some game details are unavailable. Reconnecting…')).toBeVisible();
    await expect(page.getByText('Waiting for an opponent')).toBeVisible();
});

test('a missing different game does not clear the remembered active game', async ({ page, stack }) => {
    await page.goto(`${stack.origin}/create`);
    await page.getByLabel('Captain name').fill('Remembered Host');
    await page.getByRole('button', { name: 'Create game' }).click();
    await expect(page).toHaveURL(/\/games\//);
    const remembered = await page.evaluate(() => localStorage.getItem('battleship.currentGameId'));
    if (!remembered) throw new Error('Expected created game ID to be stored.');
    const alternateId = `${remembered.slice(0, -1)}${remembered.endsWith('A') ? 'B' : 'A'}`;
    await page.goto(`${stack.origin}/games/${alternateId}`);
    await expect(page.getByRole('heading', { name: /no longer available/ })).toBeVisible();
    await page.goto(`${stack.origin}/`);
    await expect(page.getByRole('button', { name: 'Return to your game' })).toBeVisible();
    await page.getByRole('button', { name: 'Return to your game' }).click();
    await expect(page).toHaveURL(new RegExp(`/games/${remembered}$`));
});
