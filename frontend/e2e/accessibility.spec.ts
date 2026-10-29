import { devices, type Locator, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mkdir, readdir, writeFile } from 'node:fs/promises';
import { expect, test } from './fixtures/stack.js';
import { prepareGame, readGame, type Snapshot } from './helpers/playGame.js';

const sizes = [
    { name: 'portrait', width: 390, height: 844 },
    { name: 'landscape', width: 844, height: 390 },
    { name: 'laptop', width: 1280, height: 800 },
] as const;
const surfaces = [
    'home',
    'create',
    'join',
    'waiting',
    'qr',
    'placement',
    'ship-action',
    'ready',
    'gameplay',
    'checking',
    'refusal',
    'results',
    'abandoned',
    'unavailable',
    'rules',
    'settings',
    'replace',
    'leave',
    'resign',
];

async function noOverflow(page: Page) {
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
}

async function scan(page: Page) {
    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations.filter((issue) => issue.impact === 'serious' || issue.impact === 'critical')).toEqual([]);
}

// Restoring emoji presentation on the permanent brand/settings symbols must fail.
test('header symbols use text presentation and static dialogs contain no emoji', async ({ page, stack }) => {
    await page.goto(stack.origin);
    await expect(page.locator('.brand [aria-hidden="true"]')).toHaveText('⚓\uFE0E');
    await expect(
        page.getByRole('button', { name: 'Settings', exact: true }).locator('[aria-hidden="true"]'),
    ).toHaveText('⚙\uFE0E');
    await page.getByRole('button', { name: 'Settings', exact: true }).click();
    expect(await page.getByRole('dialog', { name: 'Settings', exact: true }).innerText()).not.toMatch(
        /\p{Extended_Pictographic}/u,
    );
});

test('Rules content scrolls from the keyboard and Close stays reachable', async ({ page, stack }) => {
    for (const size of sizes) {
        await page.setViewportSize(size);
        await page.goto(stack.origin);
        await page.getByRole('button', { name: 'Game rules', exact: true }).first().click();
        const dialog = page.getByRole('dialog', { name: 'Game rules', exact: true });
        const body = dialog.locator('.dialog-body');
        const close = dialog.getByRole('button', { name: 'Close', exact: true });
        await close.focus();
        await page.keyboard.press('Shift+Tab');
        await expect(body).toBeFocused();
        const overflow = await body.evaluate((node) => node.scrollHeight > node.clientHeight);
        await page.keyboard.press('End');
        if (overflow) await expect.poll(() => body.evaluate((node) => node.scrollTop)).toBeGreaterThan(0);
        await expect(body.locator('section').last().locator('dd').last()).toBeInViewport({ ratio: 1 });
        await page.keyboard.press('Tab');
        await expect(close).toBeFocused();
        await expect(close).toBeInViewport({ ratio: 1 });
        await page.keyboard.press('Enter');
        await expect(dialog).toBeHidden();
    }
});

// Removing the narrow container rule must expose both boards and fail the portrait assertions.
// Giving a board min-width:900px must fail noOverflow at 390×844.
test('three CSS layouts and tab keyboard navigation survive reflow and enlarged text', async ({
    page,
    browser,
    stack,
}) => {
    const { guest } = await prepareGame(page, browser, stack.origin, 'Sea Battle');
    try {
        for (const size of sizes) {
            await page.setViewportSize(size);
            if (size.name === 'portrait') {
                await expect(page.getByRole('tablist')).toBeVisible();
                await expect(page.getByRole('grid')).toHaveCount(1);
                const enemy = page.getByRole('tab', { name: 'Enemy waters' });
                const own = page.getByRole('tab', { name: 'Your fleet' });
                await enemy.focus();
                await page.keyboard.press('ArrowRight');
                await expect(own).toBeFocused();
                await expect(own).toHaveAttribute('aria-selected', 'true');
                await expect(page.getByRole('tabpanel', { name: 'Your fleet' })).toBeVisible();
                await expect(page.getByRole('grid', { name: 'Your fleet' })).toBeVisible();
                await page.keyboard.press('Home');
                await expect(enemy).toBeFocused();
                await page.keyboard.press('End');
                await expect(own).toBeFocused();
                await page.keyboard.press('ArrowRight');
                await expect(enemy).toBeFocused();
                await page.keyboard.press('ArrowLeft');
                await expect(own).toBeFocused();
                await page.keyboard.press('Home');
            } else {
                await expect(page.getByRole('tablist')).toBeHidden();
                await expect(page.getByRole('grid')).toHaveCount(2);
                const boards = await page.getByRole('grid').all();
                for (const board of boards) await expect(board).toBeVisible();
                if (size.name === 'landscape') {
                    expect(await page.locator('.app-bar').evaluate((bar) => getComputedStyle(bar).position)).toBe(
                        'static',
                    );
                    for (const board of boards) expect((await board.boundingBox())?.width).toBeLessThanOrEqual(272);
                }
            }
            await noOverflow(page);
            await expect(page.getByRole('button', { name: 'Refresh', exact: true })).toBeVisible();
            await expect(page.locator('.turn-pill')).toBeVisible();
            await expect(page.locator('.gameplay-foot')).toBeVisible();
        }
        // 640×400 is a 200% zoom reflow proxy for 1280×800, not literal browser zoom.
        for (const zoomProxy of [true, false]) {
            await page.setViewportSize(zoomProxy ? { width: 640, height: 400 } : sizes[0]);
            await page.evaluate((enlarge) => {
                document.documentElement.style.fontSize = enlarge ? '200%' : '';
            }, !zoomProxy);
            await noOverflow(page);
            for (const name of ['Refresh', 'Settings'])
                await expect(page.getByRole('button', { name, exact: true })).toBeInViewport();
            await expect(page.locator('.turn-pill')).toBeVisible();
            const refreshed = page.waitForResponse(
                (response) => response.request().method() === 'GET' && /\/api\/v1\/games\/[^/]+$/.test(response.url()),
            );
            await page.getByRole('button', { name: 'Refresh', exact: true }).click();
            expect((await refreshed).ok()).toBeTruthy();
            await page.getByRole('button', { name: 'Settings', exact: true }).click();
            await page.getByRole('button', { name: 'Resign match', exact: true }).click();
            await expect(page.getByRole('dialog', { name: 'Resign match?', exact: true })).toBeVisible();
            await noOverflow(page);
            await page.keyboard.press('Escape');
            await page.keyboard.press('Escape');
        }
    } finally {
        await guest.close();
    }
});

async function keyboardActivate(page: Page, target: Locator) {
    await expect(target).toBeVisible();
    for (let i = 0; i < 100; i++) {
        if (await target.evaluate((element) => element === document.activeElement)) {
            await page.keyboard.press('Enter');
            return;
        }
        await page.keyboard.press('Tab');
    }
    throw new Error(
        `Keyboard could not reach ${(await target.getAttribute('aria-label')) ?? (await target.textContent()) ?? 'control'}`,
    );
}

async function keyboardName(page: Page, name: string) {
    const input = page.getByLabel('Captain name');
    for (let i = 0; i < 100; i++) {
        if (await input.evaluate((element) => element === document.activeElement)) {
            await page.keyboard.insertText(name);
            return;
        }
        await page.keyboard.press('Tab');
    }
    throw new Error('Name input is unreachable by Tab');
}

async function focusContained(page: Page, title: string) {
    const dialog = page.getByRole('dialog', { name: title, exact: true });
    await expect(dialog).toBeVisible();
    await expect(dialog).toHaveAccessibleName(title);
    for (const key of ['Shift+Tab', 'Tab', 'Tab', 'Shift+Tab']) {
        await page.keyboard.press(key);
        expect(await dialog.evaluate((element) => element.contains(document.activeElement))).toBe(true);
    }
}

async function dialogRoundTrip(page: Page, opener: Locator, title: string, capture?: () => Promise<void>) {
    await opener.click();
    await focusContained(page, title);
    await capture?.();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog', { name: title, exact: true })).toBeHidden();
    await expect(opener).toBeFocused();
}

async function waitCommand(page: Page, action: () => Promise<void>) {
    const response = page.waitForResponse(
        (entry) => entry.request().method() === 'POST' && entry.url().endsWith('/commands'),
    );
    await action();
    const answer = await response;
    expect(answer.ok()).toBeTruthy();
    return (await answer.json()) as Snapshot;
}

// Removing grid navigation or hiding an essential control breaks this Tab/Enter-only path.
for (const size of sizes) {
    test(`keyboard-only create join ready shots resign at ${size.name}`, async ({ browser, stack }) => {
        test.setTimeout(120_000);
        const hostContext = await browser.newContext({ viewport: size });
        const guestContext = await browser.newContext({ viewport: size });
        const host = await hostContext.newPage();
        const guest = await guestContext.newPage();
        try {
            await host.goto(stack.origin);
            await keyboardActivate(host, host.getByRole('button', { name: 'New game', exact: true }));
            await keyboardActivate(host, host.getByRole('radio').filter({ hasText: 'Sea Battle' }));
            await keyboardName(host, 'Keyboard host');
            const created = host.waitForResponse(
                (r) => r.request().method() === 'POST' && r.url().endsWith('/api/v1/games'),
            );
            await keyboardActivate(host, host.getByRole('button', { name: 'Create game', exact: true }));
            const snapshot = (await (await created).json()) as Snapshot;
            if (!snapshot.invitationUrl) throw new Error('Invitation missing');
            await guest.goto(snapshot.invitationUrl);
            await keyboardName(guest, 'Keyboard guest');
            await keyboardActivate(guest, guest.getByRole('button', { name: 'Join game', exact: true }));
            for (const player of [host, guest]) {
                await expect(player.getByRole('grid', { name: 'Your sea' })).toBeVisible();
                await waitCommand(player, () =>
                    keyboardActivate(player, player.getByRole('button', { name: 'Random', exact: true })),
                );
                await keyboardActivate(player, player.getByRole('button', { name: 'Ready', exact: true }));
                await waitCommand(player, () =>
                    keyboardActivate(player, player.getByRole('button', { name: 'Commit and wait', exact: true })),
                );
            }
            await host.emulateMedia({ forcedColors: 'active', reducedMotion: 'reduce' });
            await guest.emulateMedia({ forcedColors: 'active', reducedMotion: 'reduce' });
            for (let i = 0; i < 4; i++) {
                const turn = (await readGame(host, snapshot.gameId)).turn;
                const shooter = turn === 'YOU' ? host : guest;
                await expect(shooter.getByText('Your turn · Tap an enemy cell', { exact: true })).toBeVisible();
                const board = shooter.getByRole('grid', { name: 'Enemy waters' });
                const next = board.getByRole('gridcell', { name: /, unknown$/ }).first();
                const label = await next.getAttribute('aria-label');
                if (!label) throw new Error('Unknown cell lacks coordinate');
                const match = /^([A-Z]+)(\d+),/.exec(label);
                const columnName = match?.[1];
                const rowName = match?.[2];
                if (!columnName || !rowName) throw new Error('Invalid cell coordinate');
                const column = columnName.charCodeAt(0) - 65;
                const row = Number(rowName) - 1;
                const entry = board.locator('[role="gridcell"][tabindex="0"]');
                for (let tab = 0; tab < 100; tab++) {
                    if (await entry.evaluate((cell) => cell === document.activeElement)) break;
                    await shooter.keyboard.press('Tab');
                }
                await expect(entry).toBeFocused();
                await shooter.keyboard.press('Control+Home');
                for (let c = 0; c < column; c++) await shooter.keyboard.press('ArrowRight');
                for (let r = 0; r < row; r++) await shooter.keyboard.press('ArrowDown');
                await expect(next).toBeFocused();
                const result = await waitCommand(shooter, () => shooter.keyboard.press('Space'));
                const shot = result.lastShot;
                expect(shot).toBeTruthy();
                await expect(board.getByRole('gridcell', { name: label, exact: true })).toHaveCount(0);
                const changed = board.getByRole('gridcell', {
                    name: new RegExp(`^${columnName}${rowName}, (miss|hit|sunk)$`),
                });
                await expect(changed).toBeVisible();
                await expect(changed).toHaveAttribute('data-state', /^(miss|hit|sunk)$/);
                expect(await shooter.evaluate(() => document.getAnimations().length)).toBe(0);
                await noOverflow(shooter);
            }
            await keyboardActivate(host, host.getByRole('button', { name: 'Settings', exact: true }));
            await keyboardActivate(host, host.getByRole('button', { name: 'Resign match', exact: true }));
            await waitCommand(host, () =>
                keyboardActivate(
                    host,
                    host
                        .getByRole('dialog', { name: 'Resign match?' })
                        .getByRole('button', { name: 'Resign match', exact: true }),
                ),
            );
            await expect(guest.getByRole('heading', { name: 'Victory', exact: true })).toBeVisible();
            await expect(host.getByRole('heading', { name: 'Defeat', exact: true })).toBeVisible();
        } finally {
            await hostContext.close();
            await guestContext.close();
        }
    });
}

// A Refresh that appears only on hover must fail the native touch tap/GET proof.
test('Pixel 7 tap journey, native touch drag and single Refresh read', async ({ browser, stack }) => {
    test.setTimeout(120_000);
    const context = await browser.newContext({ ...devices['Pixel 7'] });
    const guestContext = await browser.newContext({ ...devices['Pixel 7'] });
    const page = await context.newPage();
    page.setDefaultTimeout(10_000);
    const guest = await guestContext.newPage();
    try {
        await page.goto(stack.origin);
        expect(await page.evaluate(() => matchMedia('(hover: none)').matches)).toBe(true);
        await page.getByRole('button', { name: 'New game', exact: true }).tap();
        await page.getByRole('radio').filter({ hasText: 'Sea Battle' }).tap();
        await page.getByLabel('Captain name').tap();
        await page.getByLabel('Captain name').fill('Touch host'); // Text entry; every action uses native tap.
        const created = page.waitForResponse(
            (r) => r.request().method() === 'POST' && r.url().endsWith('/api/v1/games'),
        );
        await page.getByRole('button', { name: 'Create game', exact: true }).tap();
        const snapshot = (await (await created).json()) as Snapshot;
        if (!snapshot.invitationUrl) throw new Error('Invitation missing');
        await guest.goto(snapshot.invitationUrl);
        await guest.getByLabel('Captain name').tap();
        await guest.getByLabel('Captain name').fill('Touch guest');
        await guest.getByRole('button', { name: 'Join game', exact: true }).tap();
        await expect(page.getByRole('grid', { name: 'Your sea' })).toBeVisible();
        await page.getByRole('gridcell', { name: 'A1, water', exact: true }).tap();
        await waitCommand(page, () => page.getByRole('button', { name: 'Apply', exact: true }).tap());
        const before = await readGame(page, snapshot.gameId);
        const ship = before.yourBoard.ships.find((entry) => entry.cells.length > 0);
        if (!ship) throw new Error('Placed touch-drag ship absent');
        const source = page.getByRole('gridcell', { name: 'A1, ship', exact: true });
        await source.scrollIntoViewIfNeeded();
        const from = await source.boundingBox();
        const to = await page.getByRole('gridcell', { name: 'C3, water', exact: true }).boundingBox();
        if (!from || !to) throw new Error('Touch source or destination absent');
        const cdp = await context.newCDPSession(page);
        await waitCommand(page, async () => {
            await cdp.send('Input.dispatchTouchEvent', {
                type: 'touchStart',
                touchPoints: [{ x: from.x + from.width / 2, y: from.y + from.height / 2 }],
            });
            for (let i = 1; i <= 10; i++)
                await cdp.send('Input.dispatchTouchEvent', {
                    type: 'touchMove',
                    touchPoints: [
                        {
                            x: from.x + from.width / 2 + ((to.x - from.x) * i) / 10,
                            y: from.y + from.height / 2 + ((to.y - from.y) * i) / 10,
                        },
                    ],
                });
            await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
        });
        expect(
            (await readGame(page, snapshot.gameId)).yourBoard.ships.find((entry) => entry.shipId === ship.shipId)
                ?.anchor,
        ).toEqual({ rowIndex: 2, columnIndex: 2 });
        for (const player of [page, guest]) {
            await waitCommand(player, () => player.getByRole('button', { name: 'Random', exact: true }).tap());
            await player.getByRole('button', { name: 'Ready', exact: true }).tap();
            await waitCommand(player, () => player.getByRole('button', { name: 'Commit and wait', exact: true }).tap());
        }
        await expect(page.getByRole('grid', { name: 'Enemy waters' })).toBeVisible();
        // Settle initial GET before counting only the deliberate refresh.
        const requests: string[] = [];
        page.on('request', (request) => {
            if (request.method() === 'GET' && request.url().endsWith(`/api/v1/games/${snapshot.gameId}`))
                requests.push(request.url());
        });
        const refreshed = page.waitForResponse(
            (r) => r.request().method() === 'GET' && r.url().endsWith(`/api/v1/games/${snapshot.gameId}`),
        );
        await page.getByRole('button', { name: 'Refresh', exact: true }).tap();
        expect((await refreshed).ok()).toBeTruthy();
        await page.waitForTimeout(300);
        expect(requests).toHaveLength(1);
        for (let i = 0; i < 4; i++) {
            const shooter = (await readGame(page, snapshot.gameId)).turn === 'YOU' ? page : guest;
            await expect(shooter.getByText('Your turn · Tap an enemy cell', { exact: true })).toBeVisible();
            await waitCommand(shooter, () =>
                shooter
                    .getByRole('grid', { name: 'Enemy waters' })
                    .getByRole('gridcell', { name: /, unknown$/ })
                    .first()
                    .tap(),
            );
        }
        await page.getByRole('button', { name: 'Settings', exact: true }).tap();
        await page.getByRole('button', { name: 'Resign match', exact: true }).tap();
        await waitCommand(page, () =>
            page
                .getByRole('dialog', { name: 'Resign match?' })
                .getByRole('button', { name: 'Resign match', exact: true })
                .tap(),
        );
        await expect(page.getByRole('heading', { name: 'Defeat', exact: true })).toBeVisible();
        await expect(guest.getByRole('heading', { name: 'Victory', exact: true })).toBeVisible();
    } finally {
        await context.close();
        await guestContext.close();
    }
});

test('emoji preference gives transient fire and miss feedback only and respects reduced motion', async ({
    page,
    browser,
    stack,
}) => {
    const { guest, gameId } = await prepareGame(page, browser, stack.origin, 'Sea Battle');
    try {
        for (const mode of ['off', 'on', 'reduced'] as const) {
            for (const player of [page, guest]) {
                await player.emulateMedia({ reducedMotion: mode === 'reduced' ? 'reduce' : 'no-preference' });
                await player.getByRole('button', { name: 'Settings', exact: true }).click();
                const dialog = player.locator('dialog[open]');
                await expect(
                    dialog.getByText('Brief firing and miss animations only. Disabled with reduced motion.', {
                        exact: true,
                    }),
                ).toBeVisible();
                await dialog.getByRole('checkbox', { name: /Emoji overlays/ }).setChecked(mode !== 'off');
                await dialog.getByRole('combobox').selectOption('uk');
                await expect(
                    dialog.getByText('Лише короткі анімації пострілу та промаху. Вимкнено за зменшеного руху.', {
                        exact: true,
                    }),
                ).toBeVisible();
                await dialog.locator('select').selectOption('en');
                await player.getByRole('button', { name: 'Done', exact: true }).click();
            }
            const snapshot = await readGame(page, gameId);
            const shooter = snapshot.turn === 'YOU' ? page : guest;
            const other = shooter === page ? guest : page;
            const targetSnapshot = await readGame(other, gameId);
            let coordinate: { rowIndex: number; columnIndex: number } | undefined;
            for (let rowIndex = 0; rowIndex < targetSnapshot.yourBoard.grid.length && !coordinate; rowIndex++) {
                const columnIndex =
                    targetSnapshot.yourBoard.grid[rowIndex]?.findIndex((state) => state === 'WATER') ?? -1;
                if (columnIndex >= 0) coordinate = { rowIndex, columnIndex };
            }
            if (!coordinate) throw new Error('No empty cell remains');
            const label = `${String.fromCharCode(65 + coordinate.columnIndex)}${String(coordinate.rowIndex + 1)}, unknown`;
            let release: () => void = () => {
                throw new Error('Shot barrier absent');
            };
            const barrier = new Promise<void>((resolve) => {
                release = resolve;
            });
            await shooter.route('**/api/v1/games/*/commands', async (route) => {
                await barrier;
                await route.continue();
            });
            const response = shooter.waitForResponse((entry) => entry.url().endsWith('/commands'));
            await shooter
                .getByRole('grid', { name: 'Enemy waters' })
                .getByRole('gridcell', { name: label, exact: true })
                .click();
            if (mode === 'on') await expect(shooter.locator('.cell-emoji')).toHaveText('🚀');
            else expect(await shooter.locator('.cell-emoji').count()).toBe(0);
            release();
            const result = (await (await response).json()) as Snapshot;
            expect(result.lastShot?.result).toBe('MISS');
            if (mode === 'on') await expect(shooter.locator('.cell-emoji')).toHaveText('💦');
            await expect(shooter.locator('.cell-emoji')).toHaveCount(0);
            await shooter.unroute('**/api/v1/games/*/commands');
        }
        const snapshot = await readGame(page, gameId);
        const shooter = snapshot.turn === 'YOU' ? page : guest;
        const targetSnapshot = await readGame(shooter === page ? guest : page, gameId);
        const anchor = targetSnapshot.yourBoard.ships.find((ship) => ship.length === 1)?.anchor;
        if (!anchor) throw new Error('Single-cell ship absent');
        const label = `${String.fromCharCode(65 + anchor.columnIndex)}${String(anchor.rowIndex + 1)}, unknown`;
        await waitCommand(shooter, () =>
            shooter
                .getByRole('grid', { name: 'Enemy waters' })
                .getByRole('gridcell', { name: label, exact: true })
                .click(),
        );
        const revealed = shooter.locator('.board-cell[data-state="revealed_water"]').first();
        await expect(revealed).toHaveCount(1);
        expect(await revealed.evaluate((node) => getComputedStyle(node).borderStyle)).toBe('solid');
        expect(await revealed.evaluate((node) => getComputedStyle(node).backgroundImage)).toContain(
            'repeating-linear-gradient',
        );
        expect(await revealed.locator('[aria-hidden="true"]').count()).toBe(0);
        await shooter.emulateMedia({ forcedColors: 'active', reducedMotion: 'reduce' });
        expect(await revealed.evaluate((node) => getComputedStyle(node).borderStyle)).toBe('inset');
        const water = shooter.locator('.board-cell[data-state="water"]').first();
        expect(await water.evaluate((node) => getComputedStyle(node).borderStyle)).toBe('solid');
        expect(await revealed.evaluate((node) => getComputedStyle(node).borderWidth)).not.toBe(
            await water.evaluate((node) => getComputedStyle(node).borderWidth),
        );
    } finally {
        await guest.close();
    }
});

// Removing labelled dialogs, focus restoration, screen widgets or responsive tracks fails this real-state matrix.
for (const size of sizes)
    for (const theme of ['dark', 'light'] as const) {
        test(`19 surfaces, axe and dialog rules ${size.name} ${theme}`, async ({ browser, stack }) => {
            test.setTimeout(180_000);
            const context = await browser.newContext({ viewport: size });
            const guestContext = await browser.newContext({ viewport: size });
            const page = await context.newPage();
            const guest = await guestContext.newPage();
            const captured: string[] = [];
            const capture = async (surface: string, screen = page, axe = false) => {
                await noOverflow(screen);
                if (axe) await scan(screen);
                if (surface === 'qr') {
                    expect(await screen.locator('.toast').count()).toBe(0);
                    if (size.name === 'landscape') {
                        const dialog = screen.getByRole('dialog', { name: 'Invitation QR code', exact: true });
                        await expect(
                            dialog.getByRole('heading', { name: 'Invitation QR code', exact: true }),
                        ).toBeInViewport({ ratio: 1 });
                        await expect(dialog.locator('canvas')).toBeInViewport({ ratio: 1 });
                    }
                }
                if (surface === 'rules') {
                    const dialog = screen.getByRole('dialog', { name: 'Game rules', exact: true });
                    for (const part of ['head', 'body', 'actions']) {
                        const element = dialog.locator(`.dialog-${part}`);
                        await expect(element).toHaveCount(1);
                        expect(
                            await element.evaluate((node) => parseFloat(getComputedStyle(node).paddingInlineStart)),
                        ).toBeGreaterThanOrEqual(16);
                    }
                    const backdrop = await dialog.evaluate(
                        (node) => getComputedStyle(node, '::backdrop').backdropFilter,
                    );
                    expect(backdrop).toMatch(/blur\(/);
                    const cards = dialog.locator('.rules-dialog-card');
                    await expect(cards).toHaveCount(2);
                    for (const card of await cards.all()) {
                        expect(
                            await card.evaluate((node) => parseFloat(getComputedStyle(node).padding)),
                        ).toBeGreaterThanOrEqual(12);
                        expect(await card.evaluate((node) => node.scrollWidth <= node.clientWidth)).toBe(true);
                    }
                    await expect(dialog.getByRole('button', { name: 'Close', exact: true })).toBeInViewport({
                        ratio: 1,
                    });
                }
                if (surface === 'create' || surface === 'join') {
                    const input = screen.getByLabel('Captain name');
                    expect(await input.evaluate((node) => getComputedStyle(node).borderRadius)).not.toBe('0px');
                    expect(await input.evaluate((node) => getComputedStyle(node).backgroundColor)).toBe(
                        await screen.locator('.app-bar').evaluate((node) => {
                            const probe = document.createElement('div');
                            probe.style.background = 'var(--surface-0)';
                            node.append(probe);
                            const color = getComputedStyle(probe).backgroundColor;
                            probe.remove();
                            return color;
                        }),
                    );
                }
                if (surface === 'waiting' || surface === 'placement') {
                    const leave = screen.getByRole('button', { name: 'Leave game', exact: true });
                    const heading = screen.getByRole('heading', { level: 1 });
                    const buttonBox = await leave.boundingBox();
                    const headingBox = await heading.boundingBox();
                    if (!buttonBox || !headingBox) throw new Error('Heading or leave control absent');
                    expect(
                        buttonBox.x >= headingBox.x + headingBox.width ||
                            buttonBox.y >= headingBox.y + headingBox.height,
                    ).toBe(true);
                    const owner = screen.locator(surface === 'waiting' ? '.waiting-screen' : '.placement-screen');
                    expect(await owner.getByRole('button', { name: 'Leave game', exact: true }).count()).toBe(1);
                }
                if (surface === 'results') {
                    expect(await screen.locator('.result-summary').textContent()).not.toMatch(
                        /\p{Extended_Pictographic}/u,
                    );
                }
                if (['placement', 'gameplay', 'results'].includes(surface)) {
                    const ordinary = screen.locator(
                        '.board-cell:is([data-state="water"], [data-state="ship"], [data-state="unknown"], [data-state="revealed_water"])',
                    );
                    for (const cell of await ordinary.all()) {
                        expect(await cell.locator('[aria-hidden="true"]').allTextContents()).toEqual([]);
                        await expect(cell).toHaveAttribute('aria-label', /^[A-Z]+[0-9]+, .+/);
                    }
                    for (const state of ['ship', 'miss', 'revealed_water', 'hit', 'sunk']) {
                        const cell = screen.locator(`.board-cell[data-state="${state}"]`).first();
                        if ((await cell.count()) === 0) continue;
                        if (state === 'hit' || state === 'sunk') {
                            await expect(cell.locator('.cell-mark')).toHaveText(state === 'hit' ? '×' : '✕');
                        } else {
                            expect(await cell.evaluate((node) => getComputedStyle(node).borderStyle)).toBe(
                                state === 'ship' ? 'double' : state === 'miss' ? 'dashed' : 'solid',
                            );
                        }
                    }
                    expect(await screen.locator('.cell-emoji').count()).toBe(0);
                }
                const staticContent = await screen.locator('body').evaluate((node) => {
                    const copy = node.cloneNode(true) as HTMLElement;
                    copy.querySelectorAll('.cell-emoji').forEach((element) => {
                        element.remove();
                    });
                    return copy.textContent.replaceAll('⚓\uFE0E', '').replaceAll('⚙\uFE0E', '');
                });
                expect(staticContent).not.toMatch(/\p{Extended_Pictographic}/u);
                await mkdir('test-results/visual', { recursive: true });
                const file = `${surface}-${size.name}-${theme}.png`;
                await screen.evaluate(() => {
                    window.scrollTo(0, 0);
                });
                await expect.poll(() => screen.evaluate(() => window.scrollY)).toBe(0);
                await screen.screenshot({ path: `test-results/visual/${file}`, fullPage: true });
                captured.push(file);
            };
            const create = async () => {
                await page.goto(`${stack.origin}/create`);
                await page.getByRole('radio').filter({ hasText: 'Sea Battle' }).click();
                await page.getByLabel('Captain name').fill('Captain Morgan');
                const response = page.waitForResponse(
                    (r) => r.request().method() === 'POST' && r.url().endsWith('/api/v1/games'),
                );
                await page.getByRole('button', { name: 'Create game', exact: true }).click();
                const snapshot = (await (await response).json()) as Snapshot;
                await expect(page.getByRole('heading', { name: 'Waiting for an opponent' })).toBeVisible();
                return snapshot;
            };
            try {
                for (const player of [page, guest]) {
                    await player.goto(stack.origin);
                    await player.getByRole('button', { name: 'Settings', exact: true }).click();
                    await player
                        .getByRole('button', { name: theme === 'dark' ? 'Dark' : 'Light', exact: true })
                        .click();
                    await player.getByRole('checkbox', { name: /Emoji overlays/ }).check();
                    await player.getByRole('button', { name: 'Done', exact: true }).click();
                }
                await capture('home', page, true);
                await dialogRoundTrip(
                    page,
                    page.getByRole('button', { name: 'Game rules', exact: true }).first(),
                    'Game rules',
                    () => capture('rules'),
                );
                await dialogRoundTrip(
                    page,
                    page.getByRole('button', { name: 'Settings', exact: true }),
                    'Settings',
                    () => capture('settings'),
                );
                await page.getByRole('button', { name: 'New game', exact: true }).click();
                await expect(page.getByRole('radio')).toHaveCount(2);
                await capture('create', page, true);
                const snapshot = await create();
                await expect(page.getByText('Game created', { exact: true })).toBeVisible();
                await capture('waiting', page, true);
                await page.getByRole('button', { name: 'Dismiss notification', exact: true }).click();
                await expect(page.locator('.toast')).toHaveCount(0);
                await dialogRoundTrip(
                    page,
                    page.getByRole('button', { name: 'Show QR', exact: true }),
                    'Invitation QR code',
                    () => capture('qr'),
                );
                await dialogRoundTrip(
                    page,
                    page.getByRole('button', { name: 'Replace invitation', exact: true }),
                    'Replace invitation?',
                    () => capture('replace'),
                );
                await dialogRoundTrip(
                    page,
                    page.getByRole('button', { name: 'Leave game', exact: true }),
                    'Leave game?',
                    () => capture('leave'),
                );
                if (!snapshot.invitationUrl) throw new Error('Invitation missing');
                await guest.goto(snapshot.invitationUrl);
                await expect(guest.getByLabel('Captain name')).toBeVisible();
                await capture('join', guest, true);
                await guest.getByLabel('Captain name').fill('Admiral Hayes');
                await guest.getByRole('button', { name: 'Join game', exact: true }).click();
                await expect(page.getByRole('grid', { name: 'Your sea' })).toBeVisible();
                if (size.name === 'portrait') {
                    const tray = page.locator('.fleet-inventory');
                    const tiles = tray.locator(':scope > .inventory-ship');
                    const tops = await tiles.evaluateAll((elements) =>
                        elements.map((element) => Math.round(element.getBoundingClientRect().top)),
                    );
                    expect(new Set(tops).size).toBe(1);
                    expect(await tray.evaluate((element) => element.scrollWidth > element.clientWidth)).toBe(true);
                    await noOverflow(page);
                    const progress = page.getByLabel('Placement progress', { exact: true });
                    await progress.scrollIntoViewIfNeeded();
                    await expect(progress).toBeInViewport({ ratio: 1 });
                    expect(await tray.evaluate((element) => element.scrollLeft)).toBeGreaterThan(0);
                    await noOverflow(page);
                    await tray.evaluate((element) => {
                        element.scrollLeft = 0;
                    });
                }
                await capture('placement', page, true);
                await dialogRoundTrip(
                    page,
                    page.getByRole('gridcell', { name: 'A1, water', exact: true }),
                    'Place ship at A1',
                    () => capture('ship-action'),
                );
                await page.getByRole('gridcell', { name: 'A1, water', exact: true }).click();
                await waitCommand(page, () => page.getByRole('button', { name: 'Apply', exact: true }).click());
                await page
                    .getByRole('gridcell', { name: 'A2, water, unavailable for placement', exact: true })
                    .click({ force: true });
                await expect(page.getByRole('alert')).toContainText('Ships cannot touch, including diagonally.');
                await expect(page.getByRole('dialog')).toHaveCount(0);
                await capture('refusal');
                await waitCommand(page, () => page.getByRole('button', { name: 'Random', exact: true }).click());
                await dialogRoundTrip(
                    page,
                    page.getByRole('button', { name: 'Ready', exact: true }),
                    'Commit this fleet?',
                    () => capture('ready'),
                );
                for (const player of [page, guest]) {
                    if (player === guest)
                        await waitCommand(player, () =>
                            player.getByRole('button', { name: 'Random', exact: true }).click(),
                        );
                    await player.getByRole('button', { name: 'Ready', exact: true }).click();
                    await waitCommand(player, () =>
                        player.getByRole('button', { name: 'Commit and wait', exact: true }).click(),
                    );
                }
                await expect(page.getByRole('grid', { name: 'Enemy waters' })).toBeVisible();
                await capture('gameplay', page, true);
                const shooter = (await readGame(page, snapshot.gameId)).turn === 'YOU' ? page : guest;
                let lose = true;
                await shooter.route('**/api/v1/games/*/commands', async (route) => {
                    if (lose) {
                        lose = false;
                        await route.fetch();
                        await route.abort();
                    } else await route.continue();
                });
                const shotOpener = shooter
                    .getByRole('grid', { name: 'Enemy waters' })
                    .getByRole('gridcell', { name: /, unknown$/ })
                    .first();
                const shotCoordinate = (await shotOpener.getAttribute('aria-label'))?.split(',')[0];
                if (!shotCoordinate) throw new Error('Shot opener lacks coordinate');
                await shotOpener.click();
                await focusContained(shooter, 'Checking shot');
                await shooter.keyboard.press('Escape');
                await expect(shooter.getByRole('dialog', { name: 'Checking shot' })).toBeVisible();
                await capture('checking', shooter);
                await waitCommand(shooter, () => shooter.getByRole('button', { name: 'Retry', exact: true }).click());
                await expect(shooter.getByRole('dialog', { name: 'Checking shot' })).toBeHidden();
                await expect(
                    shooter
                        .getByRole('grid', { name: 'Enemy waters' })
                        .getByRole('gridcell', { name: new RegExp(`^${shotCoordinate},`) }),
                ).toBeFocused();
                await page.getByRole('button', { name: 'Settings', exact: true }).click();
                const resign = page.getByRole('button', { name: 'Resign match', exact: true });
                await dialogRoundTrip(page, resign, 'Resign match?', () => capture('resign'));
                await resign.click();
                await waitCommand(page, () =>
                    page
                        .getByRole('dialog', { name: 'Resign match?' })
                        .getByRole('button', { name: 'Resign match', exact: true })
                        .click(),
                );
                await expect(page.getByRole('heading', { name: 'Defeat', exact: true })).toBeVisible();
                await capture('results', page, true);
                await page.getByRole('button', { name: 'Leave results', exact: true }).click();
                await expect(page.getByRole('heading', { name: 'Naval command center', exact: true })).toBeVisible();
                const abandoned = await create();
                if (!abandoned.invitationUrl) throw new Error('Invitation missing');
                await guest.goto(abandoned.invitationUrl);
                await guest.getByLabel('Captain name').fill('Admiral Hayes');
                await guest.getByRole('button', { name: 'Join game', exact: true }).click();
                await expect(page.getByRole('grid', { name: 'Your sea' })).toBeVisible();
                await page.getByRole('button', { name: 'Leave game', exact: true }).click();
                await page
                    .getByRole('dialog', { name: 'Leave game?' })
                    .getByRole('button', { name: 'Leave game', exact: true })
                    .click();
                await expect(guest.getByRole('heading', { name: 'Game abandoned', exact: true })).toBeVisible();
                await capture('abandoned', guest, true);
                await page.goto(`${stack.origin}/games/${abandoned.gameId}`);
                await expect(page.getByRole('heading', { name: /This game is no longer available/ })).toBeVisible();
                await capture('unavailable', page, true);
                expect(captured).toHaveLength(19);
                expect(captured.map((name) => name.replace(`-${size.name}-${theme}.png`, '')).sort()).toEqual(
                    [...surfaces].sort(),
                );
            } finally {
                await context.close();
                await guestContext.close();
            }
        });
    }

test('visual manifest contains 114 distinct PNGs', async () => {
    const expected = sizes
        .flatMap((size) =>
            ['dark', 'light'].flatMap((theme) => surfaces.map((surface) => `${surface}-${size.name}-${theme}.png`)),
        )
        .sort();
    const actual = (await readdir('test-results/visual')).filter((file) => file.endsWith('.png')).sort();
    expect(actual).toEqual(expected);
    await writeFile('test-results/visual/manifest.json', JSON.stringify(actual, null, 2) + '\n');
});
