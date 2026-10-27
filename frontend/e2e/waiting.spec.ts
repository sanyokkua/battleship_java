import { expect, test } from './fixtures/stack.js';
import type { components } from '../src/contract/generated/api';
import { translate } from '../src/i18n/translate';

type Snapshot = components['schemas']['GameSnapshot'];
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

async function createWaitingGame(page: import('@playwright/test').Page, origin: string, rulesetId?: string) {
    await page.goto(`${origin}/create`);
    if (rulesetId) {
        const radio = page.getByRole('radio').filter({ hasText: translate('en', `ruleset.${rulesetId}`) });
        await radio.click();
        await expect(radio).toHaveAttribute('aria-checked', 'true');
    }
    await page.getByLabel('Captain name').fill('Waiting Host');
    const responsePromise = page.waitForResponse(
        (response) => response.request().method() === 'POST' && response.url().endsWith('/api/v1/games'),
    );
    await page.getByRole('button', { name: 'Create game' }).click();
    const response = await responsePromise;
    expect(response.ok()).toBeTruthy();
    return (await response.json()) as Snapshot;
}

test('a host shares, replaces, and watches an invitation live', async ({ page, browser, stack }) => {
    await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
    const rulesetsResponse = await page.request.get(`${stack.origin}/api/v1/rulesets`);
    expect(rulesetsResponse.ok()).toBeTruthy();
    const rulesets = ((await rulesetsResponse.json()) as components['schemas']['RulesetList']).rulesets;
    const original = await createWaitingGame(page, stack.origin);
    await expect(page).toHaveURL(new RegExp(`/games/${original.gameId}$`));
    await expect(page.getByRole('status').filter({ hasText: 'Game created' })).toBeVisible();
    await expect(page.getByRole('list', { name: 'Game progress' }).getByText('Waiting')).toHaveAttribute(
        'aria-current',
        'step',
    );
    const selectedRuleset = rulesets.find((ruleset) => ruleset.id === original.rulesetId);
    expect(selectedRuleset).toBeDefined();
    if (!selectedRuleset) throw new Error('Created game ruleset was absent from listRulesets.');
    const facts = page.locator('section.waiting-screen dl.rules-facts');
    const expectedFacts = advertisedFacts(selectedRuleset);
    await expect(facts.locator('dt')).toHaveText(expectedFacts.map(({ label }) => label));
    await expect(facts.locator('dd')).toHaveText(expectedFacts.map(({ value }) => value));
    await expect(
        page.getByRole('status').filter({ hasText: 'Waiting for an opponent. This page updates automatically.' }),
    ).toBeVisible();
    await page.getByRole('button', { name: 'Dismiss notification' }).click();
    await page.reload();
    await expect(page.getByText('Game created', { exact: true })).toHaveCount(0);

    const originalInvitation = original.invitationUrl;
    expect(originalInvitation).toBeDefined();
    if (!originalInvitation) throw new Error('Created game did not include an invitation URL.');
    const secret = new URL(originalInvitation).hash.slice('#invite='.length);
    const linkInput = page.getByRole('textbox', { name: 'Invitation link' });
    expect(
        await linkInput.evaluate(
            (input, fullLink) => (input as HTMLInputElement).value === fullLink,
            originalInvitation,
        ),
    ).toBe(false);
    expect(await page.locator('html').evaluate((element, token) => element.outerHTML.includes(token), secret)).toBe(
        false,
    );
    await page.getByRole('button', { name: 'Copy link' }).click();
    await expect
        .poll(() =>
            page.evaluate(
                (value) => navigator.clipboard.readText().then((copied) => copied === value),
                originalInvitation,
            ),
        )
        .toBe(true);
    await page.getByRole('button', { name: 'Show QR' }).click();
    const qr = page.getByRole('dialog', { name: 'Invitation QR code' });
    await expect(qr.getByRole('img', { name: 'QR code for invitation link' })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(qr).not.toBeVisible();

    await page.getByRole('button', { name: 'Replace invitation' }).click();
    await expect(page.getByRole('dialog', { name: 'Replace invitation?' })).toContainText(
        'current link will stop working',
    );
    const replaceResponsePromise = page.waitForResponse(
        (response) => response.request().method() === 'POST' && response.url().endsWith('/invitation'),
    );
    await page.getByRole('button', { name: 'Replace link' }).click();
    const replaceResponse = await replaceResponsePromise;
    const updated = (await replaceResponse.json()) as Snapshot;
    expect(updated.invitationUrl === originalInvitation).toBe(false);
    await page.getByRole('button', { name: 'Copy link' }).click();
    await expect
        .poll(() =>
            page.evaluate(
                (value) => navigator.clipboard.readText().then((copied) => copied === value),
                updated.invitationUrl,
            ),
        )
        .toBe(true);

    const guestContext = await browser.newContext();
    try {
        const guest = await guestContext.newPage();
        const metaResponse = guest.waitForResponse((response) => response.url().endsWith('/api/v1/meta'));
        await guest.goto(stack.origin);
        await metaResponse;
        const xsrf = (await guestContext.cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
        const stale = await guest.request.post(`${stack.origin}/api/v1/games/${original.gameId}/join`, {
            headers: { 'X-XSRF-TOKEN': xsrf ?? '' },
            data: { displayName: 'Guest', invitationSecret: secret },
        });
        expect(stale.status()).toBe(409);
        const newInvitation = updated.invitationUrl;
        expect(newInvitation).toBeDefined();
        if (!newInvitation) throw new Error('Replacement did not include an invitation URL.');
        const newSecret = new URL(newInvitation).hash.slice('#invite='.length);
        const joined = await guest.request.post(`${stack.origin}/api/v1/games/${original.gameId}/join`, {
            headers: { 'X-XSRF-TOKEN': xsrf ?? '' },
            data: { displayName: 'Guest', invitationSecret: newSecret },
        });
        expect(joined.ok()).toBeTruthy();
        await expect(page.getByRole('heading', { name: 'Place your fleet' })).toBeVisible({ timeout: 10000 });
        await expect(page.getByRole('list', { name: 'Game progress' }).getByText('Placement')).toHaveAttribute(
            'aria-current',
            'step',
        );
    } finally {
        await guestContext.close();
    }
});

test('waiting-room facts, lifetime, copy, and QR match each advertised ruleset', async ({ page, browser, stack }) => {
    const rulesetsResponse = await page.request.get(`${stack.origin}/api/v1/rulesets`);
    expect(rulesetsResponse.ok()).toBeTruthy();
    const rulesets = ((await rulesetsResponse.json()) as components['schemas']['RulesetList']).rulesets;

    for (const ruleset of rulesets) {
        const context = await browser.newContext();
        try {
            await context.grantPermissions(['clipboard-read', 'clipboard-write']);
            const rulesPage = await context.newPage();
            const created = await createWaitingGame(rulesPage, stack.origin, ruleset.id);
            expect(created.rulesetId).toBe(ruleset.id);
            const facts = rulesPage.locator('section.waiting-screen dl.rules-facts');
            const expectedFacts = advertisedFacts(ruleset);
            await expect(facts.locator('dt')).toHaveText(expectedFacts.map(({ label }) => label));
            await expect(facts.locator('dd')).toHaveText(expectedFacts.map(({ value }) => value));
            await expect(rulesPage.getByText('Invitation lifetime:', { exact: false })).toBeVisible();
            await rulesPage.getByRole('button', { name: 'Copy link' }).click();
            await expect
                .poll(() => rulesPage.evaluate(() => navigator.clipboard.readText()))
                .toBe(created.invitationUrl);
            await rulesPage.getByRole('button', { name: 'Show QR' }).click();
            await expect(
                rulesPage.getByRole('dialog', { name: 'Invitation QR code' }).getByRole('img', {
                    name: 'QR code for invitation link',
                }),
            ).toBeVisible();
        } finally {
            await context.close();
        }
    }
});

test('a replaced host stream shows its terminal status and does not reconnect', async ({ page, stack }) => {
    const firstStreamRequests: string[] = [];
    page.on('request', (request) => {
        if (request.url().endsWith('/events')) firstStreamRequests.push(request.url());
    });
    const created = await createWaitingGame(page, stack.origin);
    await expect.poll(() => firstStreamRequests.length).toBe(1);

    const secondTab = await page.context().newPage();
    try {
        await secondTab.goto(`${stack.origin}/games/${created.gameId}`);
        await expect(
            secondTab
                .getByRole('status')
                .filter({ hasText: 'Waiting for an opponent. This page updates automatically.' }),
        ).toBeVisible();
        await expect(page.getByRole('status').filter({ hasText: 'Open in another tab' })).toBeVisible();
        await secondTab.waitForTimeout(1500);
        expect(firstStreamRequests).toHaveLength(1);
    } finally {
        await secondTab.close();
    }
});

test('presence pauses while hidden and restarts on visibility return', async ({ page, useStack }) => {
    const stack = await useStack(
        'presence',
        { frontendPort: 4174, backendPort: 8081 },
        { BATTLESHIP_PRESENCEINTERVALSECONDS: '1' },
    );
    try {
        await page.addInitScript(() => {
            let visibility: DocumentVisibilityState = 'visible';
            Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => visibility });
            Object.defineProperty(window, '__setVisibility', {
                value: (value: DocumentVisibilityState) => {
                    visibility = value;
                    document.dispatchEvent(new Event('visibilitychange'));
                },
            });
        });
        const presence: string[] = [];
        page.on('request', (request) => {
            if (request.method() === 'POST' && request.url().endsWith('/presence')) presence.push(request.url());
        });
        await createWaitingGame(page, stack.origin);
        await expect.poll(() => presence.length, { timeout: 5000 }).toBeGreaterThanOrEqual(1);
        await page.evaluate(() => {
            (window as unknown as { __setVisibility: (value: DocumentVisibilityState) => void }).__setVisibility(
                'hidden',
            );
        });
        const countWhenHidden = presence.length;
        await page.waitForTimeout(1300);
        expect(presence).toHaveLength(countWhenHidden);
        await page.evaluate(() => {
            (window as unknown as { __setVisibility: (value: DocumentVisibilityState) => void }).__setVisibility(
                'visible',
            );
        });
        await page.waitForTimeout(300);
        expect(presence).toHaveLength(countWhenHidden);
        await expect.poll(() => presence.length, { timeout: 2500 }).toBeGreaterThan(countWhenHidden);
    } finally {
        await stack.stop();
    }
});

test('invitation expiry refreshes sharing state without reload', async ({ page, useStack }) => {
    const stack = await useStack(
        'short-invitation',
        { frontendPort: 4174, backendPort: 8081 },
        { BATTLESHIP_INVITATIONLIFETIMESECONDS: '10' },
    );
    try {
        const created = await createWaitingGame(page, stack.origin);
        expect(created.invitationExpiresAt).toBeTruthy();
        const meter = page.getByLabel('Invitation lifetime').locator('xpath=..').getByRole('progressbar');
        const firstValue = await meter.evaluate((element) => (element as HTMLProgressElement).value);
        await page.waitForTimeout(1200);
        const secondValue = await meter.evaluate((element) => (element as HTMLProgressElement).value);
        expect(secondValue).toBeLessThan(firstValue);
        await expect(page.getByText('Invitation expired, sharing unavailable.')).toBeVisible({ timeout: 15000 });
        await page.getByRole('button', { name: 'Replace invitation' }).click();
        await page.getByRole('button', { name: 'Replace link' }).click();
        await expect(page.getByRole('textbox', { name: 'Invitation link' })).toHaveValue(/#invite=••••••••$/);
    } finally {
        await stack.stop();
    }
});
