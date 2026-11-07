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
    const labels = {
        WATER: 'water',
        SHIP: 'ship',
        UNKNOWN: 'unknown',
        MISS: 'miss',
        HIT: 'hit',
        SUNK: 'sunk',
        REVEALED_WATER: 'revealed water',
    } as const;
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
                .locator('[role="gridcell"][data-placement="blocked"]')
                .evaluateAll((cells) => cells.map((cell) => cell.getAttribute('aria-label'))),
        )
        .toEqual(expected.filter((label) => label.endsWith(', unavailable for placement')));
    return snapshot;
}

async function applyAt(page: Page, coordinate: string) {
    await page.getByRole('gridcell', { name: new RegExp(`^${coordinate},`) }).click();
    const response = page.waitForResponse((r) => r.url().endsWith('/commands'));
    await page
        .getByRole('dialog')
        .getByRole('button', { name: /Horizontal/ })
        .first()
        .click();
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
        await expect(page.locator('[role="gridcell"][data-placement="blocked"]')).toHaveCount(0);
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
        await expect(page.locator('[role="gridcell"][data-placement="blocked"]')).toHaveCount(0);
    } finally {
        await guest.close();
    }
});

test('contact-allowed ruleset has no unavailable placement cells', async ({ page, browser, stack }) => {
    const { guest, gameId } = await startPlacement(page, browser, stack.origin, 'Battleship Classic 2002');
    try {
        expect((await applyAt(page, 'A1')).ok()).toBeTruthy();
        expect((await boardMatchesServer(page, gameId)).placementBlockedCells).toEqual([]);
        await expect(page.locator('[role="gridcell"][data-placement="blocked"]')).toHaveCount(0);
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
        await expect(page.getByText('Fleet incomplete', { exact: true })).toHaveJSProperty('tagName', 'OUTPUT');
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
        const applied = page.waitForResponse((r) => r.url().endsWith('/commands'));
        await page.keyboard.press('Enter'); // first legal orientation receives focus
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
        await page.getByRole('gridcell', { name: 'A2, water', exact: true }).click();
        const popup = page.getByRole('dialog');
        await expect(popup.getByRole('button', { name: /Horizontal/ })).toHaveCount(0);
        await popup.getByRole('button', { name: 'Cancel', exact: true }).click();
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
        await page.mouse.move(target.x + target.width / 2, target.y + target.height / 2, { steps: 6 });
        await page.mouse.up();
        const dragged = page.waitForResponse((response) => response.url().endsWith('/commands'));
        await page
            .getByRole('dialog')
            .getByRole('button', { name: /Horizontal/ })
            .click();
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
        if (placed?.type !== 'PLACE_SHIP') throw new Error('Placement command missing');
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
        await page.getByRole('gridcell', { name: 'A1, water', exact: true }).click();
        const placed = page.waitForResponse((response) => response.url().endsWith('/commands'));
        await page
            .getByRole('dialog')
            .getByRole('button', { name: /Horizontal/ })
            .click();
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
        const moved = page.waitForResponse((response) => response.url().endsWith('/commands'));
        await page
            .getByRole('dialog')
            .getByRole('button', { name: /Horizontal/ })
            .click();
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
        const readiness = page.locator('.placement-status').getByRole('status');
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
        await expect(readiness).toHaveText('Fleet incomplete');
        await page.getByRole('button', { name: 'Random', exact: true }).click();
        await expect(page.getByRole('button', { name: 'Ready', exact: true })).toBeVisible();
        await expect(readiness).toHaveText('Fleet placed. Ready to commit.');
        await checkTray();
        await page.getByRole('button', { name: 'Clear', exact: true }).click();
        await expect(readiness).toHaveText('Fleet incomplete');
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
        await expect(readiness).toHaveText('Waiting for opponent');
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

// Selecting an invalid direction or placing before the chooser fails these assertions.
test('mouse drag offers legal directions and leaves no-fit ships available', async ({ page, browser, stack }) => {
    const { guest, gameId } = await startPlacement(page, browser, stack.origin);
    try {
        const ship = (await readGame(page, gameId)).yourBoard.ships.find((entry) => entry.length === 4);
        if (!ship) throw new Error('Four-cell ship missing');
        const commands: CommandRequest[] = [];
        page.on('request', (request) => {
            if (request.url().endsWith('/commands')) commands.push(request.postDataJSON() as CommandRequest);
        });
        const dragTo = async (coordinate: string) => {
            const sourceButton = page.getByRole('button', { name: '4 cells: 1 remaining', exact: true });
            await sourceButton.scrollIntoViewIfNeeded();
            const source = await sourceButton.boundingBox();
            const target = await page
                .getByRole('gridcell', { name: `${coordinate}, water`, exact: true })
                .boundingBox();
            if (!source || !target) throw new Error('Missing drag target');
            await page.mouse.move(source.x + source.width / 2, source.y + source.height / 2);
            await page.mouse.down();
            await page.mouse.move(target.x + target.width / 2, target.y + target.height / 2, { steps: 6 });
            await page.mouse.up();
        };
        const sourceButton = page.getByRole('button', { name: '4 cells: 1 remaining', exact: true });
        await sourceButton.scrollIntoViewIfNeeded();
        const source = await sourceButton.boundingBox();
        const board = await page.getByRole('grid', { name: 'Your sea' }).boundingBox();
        if (!source || !board) throw new Error('Missing drag boundary');
        await page.mouse.move(source.x + source.width / 2, source.y + source.height / 2);
        await page.mouse.down();
        await page.mouse.move(board.x + board.width / 2, board.y + board.height / 2, { steps: 6 });
        await page.mouse.move(board.x - 20, board.y + board.height / 2, { steps: 6 });
        await page.mouse.up();
        await expect(page.getByRole('dialog')).toHaveCount(0);
        expect(commands).toHaveLength(0);

        await dragTo('J10');
        await expect(page.getByRole('dialog')).toContainText('No ship fits here.');
        expect(commands).toHaveLength(0);
        await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click();
        await expect(page.getByRole('button', { name: '4 cells: 1 remaining', exact: true })).toBeEnabled();
        await dragTo('C3');
        await expect(page.getByRole('dialog').getByRole('button', { name: /4 cells Horizontal/ })).toBeVisible();
        await expect(page.getByRole('dialog').getByRole('button', { name: /4 cells Vertical/ })).toBeVisible();
        const placed = page.waitForResponse((response) => response.url().endsWith('/commands'));
        await page
            .getByRole('dialog')
            .getByRole('button', { name: /4 cells Vertical/ })
            .click();
        expect((await placed).ok()).toBeTruthy();
        expect(commands.map((request) => request.command)).toEqual([
            {
                type: 'PLACE_SHIP',
                shipId: ship.shipId,
                anchor: { rowIndex: 2, columnIndex: 2 },
                orientation: 'VERTICAL',
            },
        ]);
        await boardMatchesServer(page, gameId);
    } finally {
        await guest.close();
    }
});

test('one-cell click and drag place directly while a blocked drop and cancelled move preserve the board', async ({
    page,
    browser,
    stack,
}) => {
    const { guest, gameId } = await startPlacement(page, browser, stack.origin);
    try {
        const singles = (await readGame(page, gameId)).yourBoard.ships.filter((ship) => ship.length === 1);
        expect(singles).toHaveLength(4);
        const commands: CommandRequest[] = [];
        page.on('request', (request) => {
            if (request.url().endsWith('/commands')) commands.push(request.postDataJSON() as CommandRequest);
        });
        await page.getByRole('button', { name: '1 cells: 4 remaining', exact: true }).click();
        const clicked = page.waitForResponse((response) => response.url().endsWith('/commands'));
        await page.getByRole('gridcell', { name: 'A1, water', exact: true }).click();
        expect((await clicked).ok()).toBeTruthy();
        await expect(page.getByRole('dialog')).toHaveCount(0);
        expect(
            (await boardMatchesServer(page, gameId)).yourBoard.ships.find((ship) => ship.shipId === singles[0]?.shipId)
                ?.anchor,
        ).toEqual({
            rowIndex: 0,
            columnIndex: 0,
        });

        const dragOne = async (coordinate: string) => {
            const sourceButton = page.getByRole('button', { name: '1 cells: 3 remaining', exact: true });
            await sourceButton.scrollIntoViewIfNeeded();
            const source = await sourceButton.boundingBox();
            const target = await page.getByRole('gridcell', { name: new RegExp(`^${coordinate},`) }).boundingBox();
            if (!source || !target) throw new Error('Missing one-cell drag target');
            await page.mouse.move(source.x + source.width / 2, source.y + source.height / 2);
            await page.mouse.down();
            await page.mouse.move(target.x + target.width / 2, target.y + target.height / 2, { steps: 6 });
            await page.mouse.up();
        };
        await dragOne('B1');
        await expect(page.getByRole('dialog')).toContainText('No ship fits here.');
        expect(commands).toHaveLength(1);
        await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click();
        const dragged = page.waitForResponse((response) => response.url().endsWith('/commands'));
        await dragOne('C3');
        expect((await dragged).ok()).toBeTruthy();
        await expect(page.getByRole('dialog')).toHaveCount(0);
        const placed = await boardMatchesServer(page, gameId);
        expect(placed.yourBoard.ships.find((ship) => ship.shipId === singles[1]?.shipId)?.anchor).toEqual({
            rowIndex: 2,
            columnIndex: 2,
        });
        expect(commands).toHaveLength(2);

        await page.getByRole('gridcell', { name: 'A1, ship', exact: true }).click();
        await page.getByRole('button', { name: 'Move', exact: true }).click();
        await page.getByRole('button', { name: 'Cancel', exact: true }).click();
        await expect(page.getByRole('gridcell', { name: 'A1, ship', exact: true })).toBeFocused();
        expect(commands).toHaveLength(2);
        await boardMatchesServer(page, gameId);
    } finally {
        await guest.close();
    }
});

// Starting a touch drag while scrolling must issue a command and fail this test.
test('touch scrolling does not place a ship; tapping a ship and cell places it', async ({ browser, useStack }) => {
    const stack = await useStack('placement-touch', { frontendPort: 4197, backendPort: 8197 });
    const context = await browser.newContext({ ...devices['Pixel 7'] });
    const page = await context.newPage();
    let guest: Page | undefined;
    try {
        const started = await startPlacement(page, browser, stack.origin);
        guest = started.guest;
        const commands: CommandRequest[] = [];
        page.on('request', (request) => {
            if (request.url().endsWith('/commands')) commands.push(request.postDataJSON() as CommandRequest);
        });
        const source = page.getByRole('button', { name: '4 cells: 1 remaining', exact: true });
        await source.scrollIntoViewIfNeeded();
        const box = await source.boundingBox();
        if (!box) throw new Error('Missing touch source');
        const cdp = await context.newCDPSession(page);
        const from = { x: box.x + box.width / 2, y: box.y + box.height / 2 };
        await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [from] });
        const boardCell = await page.getByRole('gridcell', { name: 'C3, water', exact: true }).boundingBox();
        if (!boardCell) throw new Error('Missing board target');
        const overBoard = { x: boardCell.x + boardCell.width / 2, y: boardCell.y + boardCell.height / 2 };
        await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [overBoard] });
        await expect(page.locator('.is-drag-target')).toHaveCount(0);
        await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
        await expect(page.getByRole('dialog')).toHaveCount(0);
        expect(commands).toHaveLength(0);
        await source.tap();
        await expect(source).toHaveAttribute('aria-pressed', 'true');
        await page.getByRole('gridcell', { name: 'C3, water', exact: true }).tap();
        const placed = page.waitForResponse((response) => response.url().endsWith('/commands'));
        await page
            .getByRole('dialog')
            .getByRole('button', { name: /4 cells Horizontal/ })
            .tap();
        expect((await placed).ok()).toBeTruthy();
        expect(commands).toHaveLength(1);
        await boardMatchesServer(page, started.gameId);
    } finally {
        await guest?.close();
        await context.close();
        await stack.stop();
    }
});

test('chooser aligns single-cell and vertical choices in one right-hand column', async ({ page, browser, stack }) => {
    const { guest } = await startPlacement(page, browser, stack.origin);
    try {
        await page.getByRole('gridcell', { name: 'E5, water', exact: true }).click();
        const popup = page.getByRole('dialog', { name: 'Place ship at E5' });
        await expect(popup).toBeVisible();
        const left = async (name: RegExp) => (await popup.getByRole('button', { name }).boundingBox())?.x;
        const single = await left(/Place ship at 1 cells/);
        const vertical = await left(/4 cells Vertical/);
        const horizontal = await left(/4 cells Horizontal/);
        expect(single).toBeDefined();
        expect(single).toBeCloseTo(vertical ?? -1, 0);
        expect(horizontal).toBeLessThan((vertical ?? 0) - 20);
    } finally {
        await guest.close();
    }
});

test('compact chooser filters edge placements and moves a ship atomically', async ({ page, browser, stack }) => {
    const { guest, gameId } = await startPlacement(page, browser, stack.origin);
    try {
        await page.getByRole('gridcell', { name: 'J1, water', exact: true }).click();
        const popup = page.getByRole('dialog', { name: 'Place ship at J1' });
        await expect(popup).toBeVisible();
        await expect(popup.getByRole('button', { name: /4 cells Horizontal/ })).toHaveCount(0);
        const placed = page.waitForResponse((r) => r.url().endsWith('/commands'));
        await popup.getByRole('button', { name: /4 cells Vertical/ }).click();
        expect((await placed).ok()).toBeTruthy();
        await expect(page.getByRole('gridcell', { name: 'J1, ship', exact: true })).toBeFocused();
        await page.getByRole('gridcell', { name: 'J2, ship', exact: true }).click();
        await expect(page.getByRole('dialog', { name: 'Edit ship at J2' })).toBeVisible();
        await page.getByRole('button', { name: 'Move', exact: true }).click();
        await expect(page.getByRole('gridcell', { name: 'J2, water', exact: true })).toBeFocused();
        await expect(page.getByRole('gridcell', { name: 'J1, water', exact: true })).toBeVisible();
        await page.getByRole('button', { name: 'Cancel', exact: true }).click();
        await expect(page.getByRole('gridcell', { name: 'J1, ship', exact: true })).toBeFocused();
        const ship = (await readGame(page, gameId)).yourBoard.ships.find((s) => s.length === 4);
        await page.getByRole('gridcell', { name: 'J1, ship', exact: true }).click();
        await page.getByRole('button', { name: 'Move', exact: true }).click();
        await page.getByRole('gridcell', { name: 'A1, water', exact: true }).click();
        const moved = page.waitForResponse((r) => r.url().endsWith('/commands'));
        await page
            .getByRole('dialog')
            .getByRole('button', { name: /4 cells Horizontal/ })
            .click();
        expect((await moved).ok()).toBeTruthy();
        expect((await readGame(page, gameId)).yourBoard.ships.find((s) => s.shipId === ship?.shipId)?.anchor).toEqual({
            rowIndex: 0,
            columnIndex: 0,
        });
    } finally {
        await guest.close();
    }
});

test('placement popup ship shapes use the accent in both themes', async ({ page, browser, useStack }) => {
    const stack = await useStack('placement-colors', { frontendPort: 4198, backendPort: 8198 });
    const { guest } = await startPlacement(page, browser, stack.origin);
    try {
        const checkShapes = async () => {
            const popup = page.getByRole('dialog');
            const shapes = popup.locator('.fleet-segments i');
            expect(await shapes.count()).toBeGreaterThan(0);
            for (const theme of ['light', 'dark']) {
                await page.evaluate((value) => {
                    document.documentElement.dataset.theme = value;
                }, theme);
                const accent = await page.evaluate(() => {
                    const probe = document.createElement('i');
                    probe.style.background = 'var(--accent)';
                    document.body.append(probe);
                    const color = getComputedStyle(probe).backgroundColor;
                    probe.remove();
                    return color;
                });
                await expect(shapes.first()).toHaveCSS('background-color', accent);
                await expect(page.locator('.inventory-ship .fleet-segments i').first()).toHaveCSS(
                    'background-color',
                    accent,
                );
            }
        };
        const tile = page.getByRole('button', { name: '4 cells: 1 remaining', exact: true });
        const tileBox = await tile.boundingBox();
        if (!tileBox) throw new Error('Missing fleet tile');
        expect(tileBox.height).toBeLessThanOrEqual(52);
        await page.getByRole('gridcell', { name: 'A1, water', exact: true }).click();
        await checkShapes();
        await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click();
        await page.getByRole('button', { name: '4 cells: 1 remaining', exact: true }).click();
        await page.getByRole('gridcell', { name: 'A1, water', exact: true }).click();
        await checkShapes();
        const placed = page.waitForResponse((response) => response.url().endsWith('/commands'));
        await page
            .getByRole('dialog')
            .getByRole('button', { name: /Horizontal/ })
            .click();
        expect((await placed).ok()).toBeTruthy();
        await page.getByRole('gridcell', { name: 'A1, ship', exact: true }).click();
        await checkShapes();
    } finally {
        await guest.close();
        await stack.stop();
    }
});

test('fleet tray uses two non-overlapping rows on a phone', async ({ browser, useStack }) => {
    const stack = await useStack('placement-phone-grid', { frontendPort: 4199, backendPort: 8199 });
    const context = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    const page = await context.newPage();
    let guest: Page | undefined;
    try {
        guest = (await startPlacement(page, browser, stack.origin)).guest;
        const tiles = page.locator('.inventory-ship');
        expect(await tiles.count()).toBe(4);
        const positions = await tiles.evaluateAll((elements) =>
            elements.map((element) => {
                const box = element.getBoundingClientRect();
                const label = element.querySelector('b')?.getBoundingClientRect();
                return {
                    left: box.left,
                    right: box.right,
                    top: box.top,
                    bottom: box.bottom,
                    labelLeft: label?.left,
                    labelRight: label?.right,
                };
            }),
        );
        const [a, b, c, d] = positions;
        if (!a || !b || !c || !d) throw new Error('Missing tiles');
        expect(a.top).toBe(b.top);
        expect(c.top).toBe(d.top);
        expect(c.top).toBeGreaterThanOrEqual(a.bottom);
        expect(b.left).toBeGreaterThanOrEqual(a.right);
        expect(d.left).toBeGreaterThanOrEqual(c.right);
        for (const tile of positions) {
            expect(tile.labelLeft).toBeGreaterThanOrEqual(tile.left);
            expect(tile.labelRight).toBeLessThanOrEqual(tile.right);
        }
    } finally {
        await guest?.close();
        await context.close();
        await stack.stop();
    }
});

test('edge popup stays inside the viewport and away from placement actions', async ({ page, browser, useStack }) => {
    const stack = await useStack('popup-edge', { frontendPort: 4200, backendPort: 8200 });
    let guest: Page | undefined;
    try {
        guest = (await startPlacement(page, browser, stack.origin)).guest;
        for (const viewport of [
            { width: 1280, height: 800 },
            { width: 390, height: 844 },
        ]) {
            await page.setViewportSize(viewport);
            const cell = page.getByRole('gridcell', { name: 'J10, water', exact: true });
            await cell.scrollIntoViewIfNeeded();
            await cell.click();
            const popup = page.getByRole('dialog', { name: 'Place ship at J10' });
            await expect(popup).toBeVisible();
            const bounds = await popup.boundingBox();
            const actions = await page.locator('.placement-actions').boundingBox();
            if (!bounds || !actions) throw new Error('Popup or actions missing');
            expect(bounds.x).toBeGreaterThanOrEqual(0);
            expect(bounds.y).toBeGreaterThanOrEqual(0);
            expect(bounds.x + bounds.width).toBeLessThanOrEqual(viewport.width);
            expect(bounds.y + bounds.height).toBeLessThanOrEqual(viewport.height);
            const intersects =
                bounds.x < actions.x + actions.width &&
                bounds.x + bounds.width > actions.x &&
                bounds.y < actions.y + actions.height &&
                bounds.y + bounds.height > actions.y;
            expect(intersects).toBe(false);
            await page.keyboard.press('Escape');
        }
    } finally {
        await guest?.close();
        await stack.stop();
    }
});

test('a rejected atomic move restores the original ship after another placement wins the race', async ({
    page,
    browser,
    useStack,
}) => {
    const stack = await useStack('placement-rejected-move', { frontendPort: 4201, backendPort: 8201 });
    let guest: Page | undefined;
    try {
        const started = await startPlacement(page, browser, stack.origin);
        guest = started.guest;
        expect((await applyAt(page, 'A1')).ok()).toBeTruthy();
        const before = await readGame(page, started.gameId);
        const original = before.yourBoard.ships.find((ship) => ship.cells.length > 0);
        const competitor = before.yourBoard.ships.find((ship) => ship.cells.length === 0 && ship.length === 3);
        if (!original || !competitor) throw new Error('Expected placed and unplaced ships');
        await page.getByRole('gridcell', { name: 'A1, ship', exact: true }).click();
        await page.getByRole('button', { name: 'Move', exact: true }).click();
        await page.getByRole('gridcell', { name: 'C3, water', exact: true }).click();
        const cookie = (await page.context().cookies()).find((entry) => entry.name === 'XSRF-TOKEN')?.value;
        if (!cookie) throw new Error('Missing anti-forgery cookie');
        await page.route(
            '**/commands',
            async (route) => {
                const competing = await page.request.post(`${stack.origin}/api/v1/games/${started.gameId}/commands`, {
                    headers: { 'X-XSRF-TOKEN': cookie, Origin: stack.origin },
                    data: {
                        commandId: crypto.randomUUID(),
                        command: {
                            type: 'PLACE_SHIP',
                            shipId: competitor.shipId,
                            anchor: { rowIndex: 2, columnIndex: 2 },
                            orientation: 'HORIZONTAL',
                        },
                    },
                });
                expect(competing.ok()).toBeTruthy();
                await route.continue();
            },
            { times: 1 },
        );
        const refused = page.waitForResponse(
            (response) => response.url().endsWith('/commands') && response.status() === 409,
        );
        await page
            .getByRole('dialog')
            .getByRole('button', { name: /Horizontal/ })
            .click();
        expect((await refused).status()).toBe(409);
        await expect(page.getByRole('alert')).toBeVisible();
        await expect(page.getByRole('gridcell', { name: 'A1, ship', exact: true })).toBeVisible();
        const after = await boardMatchesServer(page, started.gameId);
        expect(after.yourBoard.ships.find((ship) => ship.shipId === original.shipId)?.cells).toEqual(original.cells);
        expect(after.yourBoard.ships.find((ship) => ship.shipId === competitor.shipId)?.anchor).toEqual({
            rowIndex: 2,
            columnIndex: 2,
        });
        await expect(page.getByLabel('Placement progress')).toContainText('2 / 10');
    } finally {
        await guest?.close();
        await stack.stop();
    }
});
