import { expect, test } from './fixtures/stack.js';
import { devices, type Page, type Browser } from '@playwright/test';
import type { components } from '../src/contract/generated/api';

type Snapshot = components['schemas']['GameSnapshot'];
type CommandRequest = components['schemas']['CommandRequest'];

async function startPlacement(page: Page, browser: Browser, origin: string, ruleset = 'Sea Battle') {
    await page.goto(`${origin}/create`);
    await page.getByRole('radio').filter({ hasText: ruleset }).click();
    await page.getByLabel('Captain name').fill('Placement host');
    const created = page.waitForResponse((r) => r.request().method() === 'POST' && r.url().endsWith('/api/v1/games'));
    await page.getByRole('button', { name: 'Create game', exact: true }).click();
    const snapshot = (await (await created).json()) as Snapshot;
    if (!snapshot.invitationUrl) throw new Error('Invitation missing');
    const guest = await browser.newPage();
    await guest.goto(`${origin}/join/${snapshot.gameId}${new URL(snapshot.invitationUrl).hash}`);
    await guest.getByLabel('Captain name').fill('Placement guest');
    await guest.getByRole('button', { name: 'Join game', exact: true }).click();
    await expect(page.getByRole('grid', { name: 'Your sea' })).toBeVisible();
    return { guest, gameId: snapshot.gameId };
}

async function readGame(page: Page, gameId: string) {
    const response = await page.request.get(`${new URL(page.url()).origin}/api/v1/games/${gameId}`);
    expect(response.ok()).toBeTruthy();
    return (await response.json()) as Snapshot;
}

async function boardMatchesServer(page: Page, gameId: string) {
    const snapshot = await readGame(page, gameId);
    const blocked = new Set(
        snapshot.placementBlockedCells?.map((cell) => `${String(cell.rowIndex)}:${String(cell.columnIndex)}`) ?? [],
    );
    const labels: Record<string, string> = {
        WATER: 'water',
        SHIP: 'ship',
        UNKNOWN: 'unknown',
        MISS: 'miss',
        HIT: 'hit',
        SUNK: 'sunk',
        REVEALED_WATER: 'revealed water',
    };
    const expected = snapshot.yourBoard.grid.flatMap((row, r) =>
        row.map(
            (state, c) =>
                `${String.fromCharCode(65 + c)}${String(r + 1)}, ${labels[state]}${blocked.has(`${String(r)}:${String(c)}`) ? ', unavailable for placement' : ''}`,
        ),
    );
    await expect
        .poll(() =>
            page.getByRole('gridcell').evaluateAll((cells) => cells.map((cell) => cell.getAttribute('aria-label'))),
        )
        .toEqual(expected);
    await expect
        .poll(() =>
            page
                .locator('[data-placement="blocked"]')
                .evaluateAll((cells) => cells.map((cell) => cell.getAttribute('aria-label'))),
        )
        .toEqual(expected.filter((label) => label.endsWith(', unavailable for placement')));
    return snapshot;
}

async function applyAt(page: Page, coordinate: string) {
    await page.getByRole('gridcell', { name: new RegExp(`^${coordinate},`) }).click();
    const response = page.waitForResponse((r) => r.url().endsWith('/commands'));
    await page.getByRole('button', { name: 'Apply', exact: true }).click();
    return response;
}

test('server-projected no-touch cells use the mockup hatch and clear with the fleet', async ({
    page,
    browser,
    stack,
}) => {
    const { guest, gameId } = await startPlacement(page, browser, stack.origin);
    try {
        expect((await readGame(page, gameId)).placementBlockedCells).toEqual([]);
        await expect(page.locator('[data-placement="blocked"]')).toHaveCount(0);
        expect((await applyAt(page, 'A1')).ok()).toBeTruthy();
        const placed = await boardMatchesServer(page, gameId);
        expect(placed.placementBlockedCells).toEqual([
            { rowIndex: 0, columnIndex: 4 },
            { rowIndex: 1, columnIndex: 0 },
            { rowIndex: 1, columnIndex: 1 },
            { rowIndex: 1, columnIndex: 2 },
            { rowIndex: 1, columnIndex: 3 },
            { rowIndex: 1, columnIndex: 4 },
        ]);
        const unavailable = page.getByRole('gridcell', {
            name: 'E1, water, unavailable for placement',
            exact: true,
        });
        await expect(unavailable).toHaveAttribute('aria-disabled', 'true');
        await expect(unavailable).toHaveCSS('cursor', 'not-allowed');
        await expect(unavailable).toHaveCSS('background-image', /repeating-linear-gradient/);
        await unavailable.click({ force: true });
        await expect(page.getByRole('alert')).toContainText('Ships cannot touch, including diagonally.');
        const cleared = page.waitForResponse((response) => response.url().endsWith('/commands'));
        await page.getByRole('button', { name: 'Clear', exact: true }).click();
        expect((await cleared).ok()).toBeTruthy();
        expect((await boardMatchesServer(page, gameId)).placementBlockedCells).toEqual([]);
        await expect(page.locator('[data-placement="blocked"]')).toHaveCount(0);
    } finally {
        await guest.close();
    }
});

test('contact-allowed ruleset has no unavailable placement cells', async ({ page, browser, stack }) => {
    const { guest, gameId } = await startPlacement(page, browser, stack.origin, 'Battleship Classic 2002');
    try {
        expect((await applyAt(page, 'A1')).ok()).toBeTruthy();
        expect((await boardMatchesServer(page, gameId)).placementBlockedCells).toEqual([]);
        await expect(page.locator('[data-placement="blocked"]')).toHaveCount(0);
    } finally {
        await guest.close();
    }
});

// Disabling arrow navigation must place at the wrong anchor and fail this journey.
test('keyboard placement uses the selected coordinate and renders the server board', async ({
    page,
    browser,
    stack,
}) => {
    const { guest, gameId } = await startPlacement(page, browser, stack.origin);
    try {
        await expect(page.getByRole('button', { name: 'Ready', exact: true })).toHaveCount(0);
        await expect(page.getByText('Fleet incomplete', { exact: true })).toBeVisible();
        await page.reload();
        await expect(page.getByRole('grid')).toBeVisible();
        for (let i = 0; i < 30; i++) {
            await page.keyboard.press('Tab');
            if (
                await page
                    .getByRole('gridcell', { name: 'A1, water', exact: true })
                    .evaluate((cell) => cell === document.activeElement)
            )
                break;
        }
        await expect(page.getByRole('gridcell', { name: 'A1, water', exact: true })).toBeFocused();
        await page.keyboard.press('ArrowRight');
        await page.keyboard.press('ArrowDown');
        await expect(page.getByRole('gridcell', { name: 'B2, water', exact: true })).toBeFocused();
        await page.keyboard.press('Enter');
        await expect(page.getByRole('dialog')).toBeVisible();
        await page.keyboard.press('Enter'); // selected ship chip
        for (let i = 0; i < 20; i++) {
            if (
                await page
                    .getByRole('button', { name: 'Apply', exact: true })
                    .evaluate((button) => button === document.activeElement)
            )
                break;
            await page.keyboard.press('Tab');
        }
        await expect(page.getByRole('button', { name: 'Apply', exact: true })).toBeFocused();
        const applied = page.waitForResponse((r) => r.url().endsWith('/commands'));
        await page.keyboard.press('Enter');
        expect((await applied).ok()).toBeTruthy();
        const snapshot = await boardMatchesServer(page, gameId);
        expect(snapshot.yourBoard.ships.find((ship) => ship.cells.length > 0)?.anchor).toEqual({
            rowIndex: 1,
            columnIndex: 1,
        });
        await expect(page.getByRole('gridcell', { name: 'B2, ship', exact: true })).toBeFocused();
        await page.keyboard.press('End');
        await expect(page.getByRole('gridcell', { name: 'J2, water', exact: true })).toBeFocused();
        await page.keyboard.press('Home');
        await expect(
            page.getByRole('gridcell', { name: 'A2, water, unavailable for placement', exact: true }),
        ).toBeFocused();
    } finally {
        await guest.close();
    }
});

// Remove+place rotation and movement must fail the exact command count assertion.
test('move and rotate are atomic; touching is refused without changing the board', async ({ page, browser, stack }) => {
    const { guest, gameId } = await startPlacement(page, browser, stack.origin);
    try {
        expect((await applyAt(page, 'A1')).ok()).toBeTruthy();
        let snapshot = await boardMatchesServer(page, gameId);
        const ship = snapshot.yourBoard.ships.find((entry) => entry.cells.length > 0);
        if (!ship) throw new Error('No placed ship');
        const commands: CommandRequest[] = [];
        page.on('request', (r) => {
            if (r.url().endsWith('/commands')) commands.push(r.postDataJSON() as CommandRequest);
        });
        await page.getByRole('gridcell', { name: 'A1, ship', exact: true }).click();
        await page.getByRole('button', { name: 'Move', exact: true }).click();
        await expect(page.locator('[data-placement="blocked"]')).toHaveCount(0);
        expect((await applyAt(page, 'C3')).ok()).toBeTruthy();
        snapshot = await boardMatchesServer(page, gameId);
        expect(commands.map((r) => r.command)).toEqual([
            {
                type: 'PLACE_SHIP',
                shipId: ship.shipId,
                anchor: { rowIndex: 2, columnIndex: 2 },
                orientation: 'HORIZONTAL',
            },
        ]);
        expect(snapshot.yourBoard.ships.find((s) => s.shipId === ship.shipId)?.anchor).toEqual({
            rowIndex: 2,
            columnIndex: 2,
        });
        commands.length = 0;
        await page.getByRole('gridcell', { name: 'C3, ship', exact: true }).click();
        const rotated = page.waitForResponse((r) => r.url().endsWith('/commands'));
        await page.getByRole('button', { name: 'Rotate', exact: true }).click();
        expect((await rotated).ok()).toBeTruthy();
        snapshot = await boardMatchesServer(page, gameId);
        expect(commands.map((r) => r.command)).toEqual([
            {
                type: 'PLACE_SHIP',
                shipId: ship.shipId,
                anchor: { rowIndex: 2, columnIndex: 2 },
                orientation: 'VERTICAL',
            },
        ]);
        expect(snapshot.yourBoard.ships.find((s) => s.shipId === ship.shipId)?.orientation).toBe('VERTICAL');
        const before = snapshot.yourBoard;
        const refresh = page.waitForResponse(
            (r) => r.request().method() === 'GET' && r.url().endsWith(`/games/${gameId}`),
        );
        const refused = await applyAt(page, 'A2');
        expect(refused.status()).toBe(409);
        expect(((await refused.json()) as { code: string }).code).toBe('placement-touching');
        await refresh;
        await expect(page.getByRole('alert')).toContainText('Ships cannot touch, including diagonally.');
        expect((await boardMatchesServer(page, gameId)).yourBoard).toEqual(before);
        await page.getByRole('gridcell', { name: 'C3, ship', exact: true }).click();
        const removed = page.waitForResponse((r) => r.url().endsWith('/commands'));
        await page.getByRole('button', { name: 'Remove', exact: true }).click();
        expect((await removed).ok()).toBeTruthy();
        expect(
            (await boardMatchesServer(page, gameId)).yourBoard.ships.every((s) => s.cells.length === 0),
        ).toBeTruthy();
    } finally {
        await guest.close();
    }
});

// Retaining a closed dialog's placed ship ID must fail the original-ship anchor assertion.
test('reopening an empty cell selects a currently unplaced ship', async ({ page, browser, stack }) => {
    const { guest, gameId } = await startPlacement(page, browser, stack.origin);
    try {
        const original = (await readGame(page, gameId)).yourBoard.ships.find((ship) => ship.length === 4);
        if (!original) throw new Error('Four-cell ship missing');
        await page.getByRole('gridcell', { name: 'A1, water', exact: true }).click();
        await page.getByRole('button', { name: 'Cancel', exact: true }).click();
        const source = await page.getByRole('button', { name: '4 cells: 1 remaining', exact: true }).boundingBox();
        const target = await page.getByRole('gridcell', { name: 'C3, water', exact: true }).boundingBox();
        if (!source || !target) throw new Error('Drag source or target missing');
        await page.mouse.move(source.x + source.width / 2, source.y + source.height / 2);
        await page.mouse.down();
        const dragged = page.waitForResponse((response) => response.url().endsWith('/commands'));
        await page.mouse.move(target.x + target.width / 2, target.y + target.height / 2, { steps: 6 });
        await page.mouse.up();
        expect((await dragged).ok()).toBeTruthy();
        const before = await boardMatchesServer(page, gameId);
        expect(before.yourBoard.ships.find((ship) => ship.shipId === original.shipId)?.anchor).toEqual({
            rowIndex: 2,
            columnIndex: 2,
        });
        const unplacedIds = before.yourBoard.ships.filter((ship) => ship.cells.length === 0).map((ship) => ship.shipId);
        const commands: CommandRequest[] = [];
        page.on('request', (request) => {
            if (request.url().endsWith('/commands')) commands.push(request.postDataJSON() as CommandRequest);
        });
        expect((await applyAt(page, 'A1')).ok()).toBeTruthy();
        const after = await boardMatchesServer(page, gameId);
        expect(after.yourBoard.ships.find((ship) => ship.shipId === original.shipId)?.anchor).toEqual({
            rowIndex: 2,
            columnIndex: 2,
        });
        expect(commands).toHaveLength(1);
        expect(commands[0]?.command).toMatchObject({ type: 'PLACE_SHIP', anchor: { rowIndex: 0, columnIndex: 0 } });
        const placed = commands[0]?.command;
        if (placed.type !== 'PLACE_SHIP') throw new Error('Placement command missing');
        expect(unplacedIds).toContain(placed.shipId);
        expect(after.yourBoard.ships.find((ship) => ship.shipId === placed.shipId)?.anchor).toEqual({
            rowIndex: 0,
            columnIndex: 0,
        });
    } finally {
        await guest.close();
    }
});

// Substituting a repeated type's unplaced sibling must fail the exact command shipId assertion.
test('activating the selected repeated-type chip moves the original ship', async ({ page, browser, stack }) => {
    const { guest, gameId } = await startPlacement(page, browser, stack.origin);
    try {
        await page.getByRole('button', { name: '3 cells: 2 remaining', exact: true }).click();
        const placed = page.waitForResponse((response) => response.url().endsWith('/commands'));
        await page.getByRole('button', { name: 'Apply', exact: true }).click();
        expect((await placed).ok()).toBeTruthy();
        const before = await boardMatchesServer(page, gameId);
        const original = before.yourBoard.ships.find((ship) => ship.length === 3 && ship.cells.length > 0);
        if (!original) throw new Error('Placed three-cell ship missing');
        const sibling = before.yourBoard.ships.find(
            (ship) => ship.shipTypeId === original.shipTypeId && ship.shipId !== original.shipId,
        );
        if (!sibling) throw new Error('Repeated-type sibling missing');
        const commands: CommandRequest[] = [];
        page.on('request', (request) => {
            if (request.url().endsWith('/commands')) commands.push(request.postDataJSON() as CommandRequest);
        });
        await page.getByRole('gridcell', { name: 'A1, ship', exact: true }).click();
        await page.getByRole('button', { name: 'Move', exact: true }).click();
        await page.getByRole('gridcell', { name: 'C3, water', exact: true }).click();
        const chip = page
            .getByRole('group', { name: 'Choose ship', exact: true })
            .getByRole('button', { pressed: true });
        await expect(chip).toHaveCount(1);
        await chip.click();
        const moved = page.waitForResponse((response) => response.url().endsWith('/commands'));
        await page.getByRole('button', { name: 'Apply', exact: true }).click();
        expect((await moved).ok()).toBeTruthy();
        const after = await boardMatchesServer(page, gameId);
        expect(commands.map((request) => request.command)).toEqual([
            {
                type: 'PLACE_SHIP',
                shipId: original.shipId,
                anchor: { rowIndex: 2, columnIndex: 2 },
                orientation: 'HORIZONTAL',
            },
        ]);
        expect(after.yourBoard.ships.find((ship) => ship.shipId === original.shipId)?.anchor).toEqual({
            rowIndex: 2,
            columnIndex: 2,
        });
        expect(after.yourBoard.ships.find((ship) => ship.shipId === sibling.shipId)?.cells).toEqual([]);
    } finally {
        await guest.close();
    }
});

// Incorrect tray derivation, ungated Ready, or sending READY before confirmation fails here.
test('random and clear update tray counts; ready requires confirmation and locks the fleet', async ({
    page,
    browser,
    stack,
}) => {
    const { guest, gameId } = await startPlacement(page, browser, stack.origin);
    try {
        const checkTray = async () => {
            const snapshot = await boardMatchesServer(page, gameId);
            for (const length of [4, 3, 2, 1]) {
                const count = snapshot.yourBoard.ships.filter(
                    (s) => s.length === length && s.cells.length === 0,
                ).length;
                await expect(
                    page.getByRole('button', {
                        name: `${String(length)} cells: ${String(count)} remaining`,
                        exact: true,
                    }),
                ).toBeVisible();
            }
            const placed = snapshot.yourBoard.ships.filter((s) => s.cells.length > 0).length;
            await expect(page.getByLabel('Placement progress')).toContainText(
                `${String(placed)} / ${String(snapshot.yourBoard.ships.length)}`,
            );
        };
        await checkTray();
        await page.getByRole('button', { name: 'Random', exact: true }).click();
        await expect(page.getByRole('button', { name: 'Ready', exact: true })).toBeVisible();
        await checkTray();
        await page.getByRole('button', { name: 'Clear', exact: true }).click();
        await expect(page.getByText('Fleet incomplete', { exact: true })).toBeVisible();
        await checkTray();
        await page.getByRole('button', { name: 'Random', exact: true }).click();
        await page.getByRole('button', { name: 'Ready', exact: true }).click();
        const confirmation = page.getByRole('dialog', { name: 'Commit this fleet?' });
        await expect(confirmation).toContainText('cannot move, rotate, or remove ships');
        expect((await readGame(page, gameId)).you.ready).toBe(false);
        await confirmation.getByRole('button', { name: 'Cancel', exact: true }).click();
        expect((await readGame(page, gameId)).you.ready).toBe(false);
        await page.getByRole('button', { name: 'Ready', exact: true }).click();
        await confirmation.getByRole('button', { name: 'Commit and wait', exact: true }).click();
        await expect(page.getByText('Waiting for opponent', { exact: true })).toBeVisible();
        expect((await readGame(page, gameId)).you.ready).toBe(true);
        await expect(page.getByRole('button', { name: /^(Ready|Unready|Random|Clear)$/ })).toHaveCount(0);
        await expect(guest.getByText('Opponent ready', { exact: true })).toBeVisible();
        await guest.getByRole('button', { name: 'Random', exact: true }).click();
        await guest.getByRole('button', { name: 'Ready', exact: true }).click();
        await guest.getByRole('button', { name: 'Commit and wait', exact: true }).click();
        await expect(page.getByRole('grid', { name: 'Your sea' })).toHaveCount(0);
        await expect(page.getByRole('grid', { name: 'Enemy waters' })).toBeVisible(); // server transitioned to PLAYING
    } finally {
        await guest.close();
    }
});

// Removing the clamp must send an invalid anchor or leave the server board unchanged.
test('mouse tray drag clamps a drop past the board edge into one placement', async ({ page, browser, stack }) => {
    const { guest, gameId } = await startPlacement(page, browser, stack.origin);
    try {
        const ship = (await readGame(page, gameId)).yourBoard.ships.find((s) => s.length === 4);
        if (!ship) throw new Error('Four-cell ship missing');
        const commands: CommandRequest[] = [];
        page.on('request', (r) => {
            if (r.url().endsWith('/commands')) commands.push(r.postDataJSON() as CommandRequest);
        });
        const source = await page.getByRole('button', { name: '4 cells: 1 remaining', exact: true }).boundingBox();
        const edge = await page.getByRole('gridcell', { name: 'J10, water', exact: true }).boundingBox();
        if (!source || !edge) throw new Error('Drag source or edge missing');
        await page.mouse.move(source.x + source.width / 2, source.y + source.height / 2);
        await page.mouse.down();
        const middle = await page.getByRole('gridcell', { name: 'C3, water', exact: true }).boundingBox();
        if (!middle) throw new Error('Preview cell missing');
        await page.mouse.move(middle.x + middle.width / 2, middle.y + middle.height / 2, { steps: 6 });
        await expect(page.locator('.is-drag-target')).toHaveAttribute('aria-label', 'C3, water');
        await page.mouse.move(edge.x + edge.width * 2, edge.y + edge.height * 2, { steps: 12 });
        await page.mouse.up();
        await expect.poll(() => commands.length).toBe(1);
        expect(commands[0]?.command).toEqual({
            type: 'PLACE_SHIP',
            shipId: ship.shipId,
            anchor: { rowIndex: 9, columnIndex: 6 },
            orientation: 'HORIZONTAL',
        });
        await expect
            .poll(
                async () =>
                    (await readGame(page, gameId)).yourBoard.ships.find((s) => s.shipId === ship.shipId)?.anchor,
            )
            .toEqual({ rowIndex: 9, columnIndex: 6 });
        await boardMatchesServer(page, gameId);
        await expect(page.getByRole('dialog')).toHaveCount(0);
        expect(commands).toHaveLength(1);
        await page.getByRole('gridcell', { name: 'A1, water', exact: true }).click();
        await expect(page.getByRole('dialog')).toBeVisible();
    } finally {
        await guest.close();
    }
});

// Mouse-only handlers must fail this native touch journey; a grabbed ship cell keeps its offset.
test('touch drag moves a placed ship with one placement and preserves orientation and grab offset', async ({
    browser,
    useStack,
}) => {
    const stack = await useStack('placed-ship-touch-drag', { frontendPort: 4197, backendPort: 8197 });
    const context = await browser.newContext({ ...devices['Pixel 7'] });
    const page = await context.newPage();
    let guest: Page | undefined;
    try {
        const started = await startPlacement(page, browser, stack.origin);
        guest = started.guest;
        const gameId = started.gameId;
        expect((await applyAt(page, 'A1')).ok()).toBeTruthy();
        const before = await boardMatchesServer(page, gameId);
        const ship = before.yourBoard.ships.find((s) => s.cells.length > 0);
        if (!ship) throw new Error('Placed ship missing');
        await page.getByRole('gridcell', { name: 'A1, ship', exact: true }).click();
        const rotated = page.waitForResponse((r) => r.url().endsWith('/commands'));
        await page.getByRole('button', { name: 'Rotate', exact: true }).click();
        expect((await rotated).ok()).toBeTruthy();
        const rotatedSnapshot = await boardMatchesServer(page, gameId);
        expect(rotatedSnapshot.placementBlockedCells?.length).toBeGreaterThan(0);
        await page.getByRole('gridcell', { name: 'A2, ship', exact: true }).scrollIntoViewIfNeeded();
        const source = await page.getByRole('gridcell', { name: 'A2, ship', exact: true }).boundingBox();
        const target = await page.getByRole('gridcell', { name: 'C5, water', exact: true }).boundingBox();
        const commands: CommandRequest[] = [];
        page.on('request', (r) => {
            if (r.url().endsWith('/commands')) commands.push(r.postDataJSON() as CommandRequest);
        });
        if (!source || !target) throw new Error('Touch source or target missing');
        const cdp = await context.newCDPSession(page);
        const from = { x: source.x + source.width / 2, y: source.y + source.height / 2 };
        const to = { x: target.x + target.width / 2, y: target.y + target.height / 2 };
        await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [from] });
        for (let i = 1; i <= 10; i++) {
            await cdp.send('Input.dispatchTouchEvent', {
                type: 'touchMove',
                touchPoints: [
                    {
                        x: from.x + ((to.x - from.x) * i) / 10,
                        y: from.y + ((to.y - from.y) * i) / 10,
                    },
                ],
            });
        }
        await expect(page.locator('[data-placement="blocked"]')).toHaveCount(0);
        await expect(page.locator('.is-drag-target')).toHaveAttribute('aria-label', 'C4, water');
        await cdp.send('Input.dispatchTouchEvent', { type: 'touchCancel', touchPoints: [] });
        await expect(page.locator('.is-drag-target')).toHaveCount(0);
        await expect(page.locator('[data-placement="blocked"]')).toHaveCount(
            rotatedSnapshot.placementBlockedCells?.length ?? 0,
        );
        expect(commands).toHaveLength(0);
        expect((await readGame(page, gameId)).yourBoard.ships.find((s) => s.shipId === ship.shipId)?.anchor).toEqual({
            rowIndex: 0,
            columnIndex: 0,
        });
        await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [from] });
        await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [to] });
        await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
        await expect.poll(() => commands.length).toBe(1);
        expect(commands[0]?.command).toEqual({
            type: 'PLACE_SHIP',
            shipId: ship.shipId,
            anchor: { rowIndex: 3, columnIndex: 2 },
            orientation: 'VERTICAL',
        });
        await expect
            .poll(
                async () =>
                    (await readGame(page, gameId)).yourBoard.ships.find((s) => s.shipId === ship.shipId)?.anchor,
            )
            .toEqual({ rowIndex: 3, columnIndex: 2 });
        const after = await boardMatchesServer(page, gameId);
        expect(after.yourBoard.ships.find((s) => s.shipId === ship.shipId)?.orientation).toBe('VERTICAL');
        await expect(page.getByRole('dialog')).toHaveCount(0);
        expect(commands).toHaveLength(1);
    } finally {
        await guest?.close();
        await context.close();
        await stack.stop();
    }
});
