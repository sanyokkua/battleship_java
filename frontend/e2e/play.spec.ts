import { expect, test } from './fixtures/stack.js';
import type { Browser, Page } from '@playwright/test';
import type { components } from '../src/contract/generated/api';

type Snapshot = components['schemas']['GameSnapshot'];
type CommandRequest = components['schemas']['CommandRequest'];

async function readGame(page: Page, gameId: string): Promise<Snapshot> {
    const response = await page.request.get(`${new URL(page.url()).origin}/api/v1/games/${gameId}`);
    expect(response.ok()).toBeTruthy();
    return (await response.json()) as Snapshot;
}

async function fixedFleet(page: Page, classic: boolean) {
    const fleet: [number, string][] = classic
        ? [
              [5, 'A1'],
              [4, 'A3'],
              [3, 'A5'],
              [3, 'E5'],
              [2, 'A7'],
          ]
        : [
              [4, 'A1'],
              [3, 'A3'],
              [3, 'E3'],
              [2, 'A5'],
              [2, 'D5'],
              [2, 'G5'],
              [1, 'A7'],
              [1, 'C7'],
              [1, 'E7'],
              [1, 'G7'],
          ];
    for (const [ship, anchor] of fleet) {
        await page.getByRole('gridcell', { name: `${anchor}, water`, exact: true }).click();
        const applied = page.waitForResponse((response) => response.url().endsWith('/commands'));
        await page
            .getByRole('button', {
                name: `Place ship at ${String(ship)} cells${ship === 1 ? '' : ' Horizontal'}`,
                exact: true,
            })
            .click();
        expect((await applied).ok()).toBeTruthy();
    }
}

async function ready(page: Page) {
    await page.getByRole('button', { name: 'Ready', exact: true }).click();
    const response = page.waitForResponse((entry) => entry.url().endsWith('/commands'));
    await page.getByRole('button', { name: 'Commit and wait', exact: true }).click();
    expect((await response).ok()).toBeTruthy();
}

async function startGame(page: Page, browser: Browser, origin: string, classic = false) {
    await page.goto(`${origin}/create`);
    await page
        .getByRole('radio')
        .filter({ hasText: classic ? 'Battleship Classic 2002' : 'Sea Battle' })
        .click();
    await page.getByLabel('Captain name').fill('Host');
    const created = page.waitForResponse(
        (response) =>
            response.request().method() === 'POST' && response.url().endsWith('/api/v1/games') && response.ok(),
    );
    await page.getByRole('button', { name: 'Create game', exact: true }).click();
    const createdResponse = await created;
    expect(createdResponse.ok(), `Create returned ${String(createdResponse.status())}`).toBeTruthy();
    const snapshot = (await createdResponse.json()) as Snapshot;
    if (!snapshot.invitationUrl) throw new Error('Invitation missing');
    const guest = await browser.newPage();
    await guest.goto(`${origin}/join/${snapshot.gameId}${new URL(snapshot.invitationUrl).hash}`);
    await guest.getByLabel('Captain name').fill('Guest');
    await guest.getByRole('button', { name: 'Join game', exact: true }).click();
    await expect(page.getByRole('grid', { name: 'Your sea' })).toBeVisible();
    await fixedFleet(page, classic);
    await fixedFleet(guest, classic);
    await ready(page);
    await ready(guest);
    await expect(page.getByRole('grid', { name: 'Enemy waters' })).toBeVisible();
    await expect(guest.getByRole('grid', { name: 'Enemy waters' })).toBeVisible();
    const hostView = await readGame(page, snapshot.gameId);
    const shooter = hostView.turn === 'YOU' ? page : guest;
    const receiver = hostView.turn === 'YOU' ? guest : page;
    await expect(shooter.getByText('Your turn · Tap an enemy cell', { exact: true })).toBeVisible();
    return { guest, gameId: snapshot.gameId, shooter, receiver };
}

async function fire(page: Page, coordinate: string) {
    const response = page.waitForResponse((entry) => entry.url().endsWith('/commands'));
    await page
        .getByRole('grid', { name: 'Enemy waters' })
        .getByRole('gridcell', { name: `${coordinate}, unknown`, exact: true })
        .click();
    expect((await response).ok()).toBeTruthy();
}

async function expectCards(page: Page, gameId: string, classic: boolean) {
    const snapshot = await readGame(page, gameId);
    const totalShips = classic ? 5 : 10;
    const totalCells = classic ? 17 : 20;
    for (const [player, board] of [
        [snapshot.you, snapshot.yourBoard],
        [snapshot.opponent, snapshot.opponentBoard],
    ] as const) {
        if (!player) throw new Error('Opponent missing');
        const card = page.getByRole('region', { name: `${player.displayName} fleet` });
        const damage = board.grid.flat().filter((state) => state === 'HIT' || state === 'SUNK').length;
        await expect(card.getByRole('group', { name: 'Ships left' })).toHaveText(
            `Ships left${String(player.shipsRemaining)}/${String(totalShips)}`,
        );
        await expect(card.getByRole('group', { name: 'Ship cells left' })).toHaveText(
            `Ship cells left${String(totalCells - damage)}/${String(totalCells)}`,
        );
    }
}

test.setTimeout(90_000);

// Removing extra-turn handling, distinct revealed water, live snapshots, or count derivation fails here.
test('Sea Battle hits retain the turn, sunk water differs from a miss, and opponent shots arrive live', async ({
    page,
    browser,
    stack,
}) => {
    const { guest, gameId, shooter, receiver } = await startGame(page, browser, stack.origin);
    try {
        await fire(shooter, 'A1');
        await expect(shooter.getByText('Your turn · Tap an enemy cell', { exact: true })).toBeVisible();
        await expect(
            shooter.getByRole('grid', { name: 'Enemy waters' }).getByRole('gridcell', { name: 'A1, hit', exact: true }),
        ).toHaveAttribute('aria-disabled', 'true');
        await expectCards(shooter, gameId, false);
        await fire(shooter, 'A7');
        await expect(
            shooter
                .getByRole('grid', { name: 'Enemy waters' })
                .getByRole('gridcell', { name: 'A7, sunk', exact: true }),
        ).toBeVisible();
        const revealed = shooter
            .getByRole('grid', { name: 'Enemy waters' })
            .getByRole('gridcell', { name: 'B7, revealed water', exact: true });
        await expect(revealed).toHaveAttribute('aria-disabled', 'true');
        await fire(shooter, 'J10');
        const miss = shooter
            .getByRole('grid', { name: 'Enemy waters' })
            .getByRole('gridcell', { name: 'J10, miss', exact: true });
        // Both are dimmed water, but only an actual missed shot has a dot.
        expect(await revealed.evaluate((cell) => getComputedStyle(cell, '::after').content)).toBe('none');
        expect(await miss.evaluate((cell) => getComputedStyle(cell, '::after').content)).toBe('""');
        // The miss dot is about 15% of the cell, and misses share the unavailable-cell hatching in both themes.
        const dotRatio = await miss.evaluate(
            (cell) => Number.parseFloat(getComputedStyle(cell, '::after').width) / cell.clientWidth,
        );
        expect(dotRatio).toBeGreaterThan(0.13);
        expect(dotRatio).toBeLessThan(0.17);
        for (const theme of ['dark', 'light']) {
            await shooter.evaluate((value) => {
                document.documentElement.dataset.theme = value;
            }, theme);
            expect(await miss.evaluate((cell) => getComputedStyle(cell).backgroundImage)).toContain(
                'repeating-linear-gradient',
            );
        }
        await expect(receiver.getByText('Your turn · Tap an enemy cell', { exact: true })).toBeVisible();
        let loads = 0;
        shooter.on('load', () => {
            loads += 1;
        });
        await fire(receiver, 'A1');
        await expect(
            shooter.getByRole('grid', { name: 'Your fleet' }).getByRole('gridcell', { name: 'A1, hit', exact: true }),
        ).toBeVisible();
        await expect(shooter.getByLabel('Shot announcement')).toHaveText('Opponent fired at A1: hit.');
        expect(loads).toBe(0);
        await expectCards(shooter, gameId, false);
        await expect(shooter.getByText('Session expires in', { exact: false })).toBeVisible();
        await expect(shooter.getByText(/snapshot v/i)).toHaveCount(0);
    } finally {
        await guest.close();
    }
});

// Computing a turn from hits locally must fail in Classic.
test('Classic hit passes the turn and all known targets stay disabled', async ({ page, browser, stack }) => {
    const { guest, gameId, shooter, receiver } = await startGame(page, browser, stack.origin, true);
    try {
        await fire(shooter, 'A1');
        await expect(receiver.getByText('Your turn · Tap an enemy cell', { exact: true })).toBeVisible();
        await expect(shooter.getByText('Opponent’s turn', { exact: true })).toBeVisible();
        await expect(shooter.getByRole('grid', { name: 'Enemy waters' }).getByRole('gridcell').first()).toHaveAttribute(
            'aria-disabled',
            'true',
        );
        await expect(
            shooter
                .getByRole('grid', { name: 'Enemy waters' })
                .getByRole('gridcell', { name: 'B1, unknown', exact: true }),
        ).toHaveAttribute('aria-disabled', 'true');
        expect((await readGame(shooter, gameId)).turn).toBe('OPPONENT');
        await expectCards(shooter, gameId, true);
    } finally {
        await guest.close();
    }
});

// Omitting the 409 refresh or rendering raw refusal text must fail this stale-view race.
test('a stale action is refused, explained and refreshed without an extra shot', async ({ page, browser, stack }) => {
    const { guest, gameId, shooter } = await startGame(page, browser, stack.origin, true);
    try {
        await shooter.route('**/events', (route) => route.abort());
        await shooter.reload();
        await expect(shooter.getByText('Your turn · Tap an enemy cell', { exact: true })).toBeVisible();
        const before = await readGame(shooter, gameId);
        const xsrf = (await shooter.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
        const direct = await shooter.request.post(`/api/v1/games/${gameId}/commands`, {
            headers: { 'X-XSRF-TOKEN': xsrf ?? '', Origin: stack.origin },
            data: {
                commandId: crypto.randomUUID(),
                command: { type: 'FIRE', target: { rowIndex: 9, columnIndex: 9 } },
            },
        });
        expect(direct.ok()).toBeTruthy();
        const refreshed = shooter.waitForResponse(
            (response) => response.request().method() === 'GET' && response.url().endsWith(`/games/${gameId}`),
        );
        const refused = shooter.waitForResponse((response) => response.url().endsWith('/commands'));
        await shooter
            .getByRole('grid', { name: 'Enemy waters' })
            .getByRole('gridcell', { name: 'I10, unknown', exact: true })
            .click();
        expect((await refused).status()).toBe(409);
        await refreshed;
        await expect(shooter.getByRole('alert')).toContainText('That action is no longer available.');
        await expect(
            shooter
                .getByRole('grid', { name: 'Enemy waters' })
                .getByRole('gridcell', { name: 'J10, miss', exact: true }),
        ).toBeVisible();
        const after = await readGame(shooter, gameId);
        const shots = (snapshot: Snapshot) =>
            snapshot.opponentBoard.grid.flat().filter((state) => ['MISS', 'HIT', 'SUNK'].includes(state)).length;
        expect(shots(after) - shots(before)).toBe(1);
    } finally {
        await guest.close();
    }
});

// A fresh Retry commandId must fail the exact body assertion and duplicate-command outcome.
test('a lost answer stays checking after SSE and Retry resends the same action once', async ({
    page,
    browser,
    stack,
}) => {
    const { guest, gameId, shooter } = await startGame(page, browser, stack.origin);
    try {
        const requests: CommandRequest[] = [];
        shooter.on('request', (request) => {
            if (request.url().endsWith('/commands')) requests.push(request.postDataJSON() as CommandRequest);
        });
        let release: (() => void) | undefined;
        const held = new Promise<void>((resolve) => {
            release = resolve;
        });
        await shooter.route(
            '**/commands',
            async (route) => {
                await route.fetch();
                await held;
                await route.abort();
            },
            { times: 1 },
        );
        await shooter
            .getByRole('grid', { name: 'Enemy waters' })
            .getByRole('gridcell', { name: 'A1, unknown', exact: true })
            .click();
        await expect(
            shooter.getByRole('grid', { name: 'Enemy waters' }).getByRole('gridcell', { name: 'A1, hit', exact: true }),
        ).toHaveAttribute('aria-busy', 'true');
        await expect(
            shooter
                .getByRole('grid', { name: 'Enemy waters' })
                .getByRole('gridcell', { name: 'B1, unknown', exact: true }),
        ).toHaveAttribute('aria-disabled', 'true');
        release?.();
        const checking = shooter.getByRole('dialog', { name: 'Checking shot' });
        await expect(checking).toBeVisible();
        await shooter.getByRole('button', { name: 'Retry', exact: true }).click();
        await expect(checking).not.toBeVisible();
        expect(requests).toHaveLength(2);
        expect(requests[1]).toEqual(requests[0]);
        const snapshot = await readGame(shooter, gameId);
        expect(snapshot.opponentBoard.grid.flat().filter((state) => state === 'HIT' || state === 'MISS')).toHaveLength(
            1,
        );
        expect(snapshot.opponentBoard.grid[0]?.[0]).toBe('HIT');
        await expect(shooter.getByRole('alert')).toHaveCount(0);
    } finally {
        await guest.close();
    }
});

// Unmounting Gameplay must not hide an unresolved action or allow SSE to settle it.
test('checking remains available when a live update finishes the game', async ({ page, browser, stack }) => {
    const { guest, gameId, shooter, receiver } = await startGame(page, browser, stack.origin);
    try {
        await shooter.route(
            '**/commands',
            async (route) => {
                await route.fetch();
                await route.abort();
            },
            { times: 1 },
        );
        await shooter
            .getByRole('grid', { name: 'Enemy waters' })
            .getByRole('gridcell', { name: 'A1, unknown', exact: true })
            .click();
        const checking = shooter.getByRole('dialog', { name: 'Checking shot' });
        await expect(checking).toBeVisible();
        const xsrf = (await receiver.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
        const resigned = await receiver.request.post(`/api/v1/games/${gameId}/commands`, {
            headers: { 'X-XSRF-TOKEN': xsrf ?? '', Origin: stack.origin },
            data: { commandId: crypto.randomUUID(), command: { type: 'RESIGN' } },
        });
        expect(resigned.ok()).toBeTruthy();
        await expect(shooter.getByRole('heading', { name: 'Match in progress', includeHidden: true })).toHaveCount(0);
        await expect(checking).toBeVisible();
        const retried = shooter.waitForResponse((response) => response.url().endsWith('/commands'));
        await shooter.getByRole('button', { name: 'Retry', exact: true }).click();
        expect((await retried).ok()).toBeTruthy();
        await expect(checking).not.toBeVisible();
        expect((await readGame(shooter, gameId)).phase).toBe('FINISHED');
    } finally {
        await guest.close();
    }
});

// A refresh that changes the displayed phase must still retain the command refusal notice.
test('a refusal remains explained when its refresh reaches results', async ({ page, browser, stack }) => {
    const { guest, gameId, shooter, receiver } = await startGame(page, browser, stack.origin, true);
    try {
        await shooter.route('**/events', (route) => route.abort());
        await shooter.reload();
        await expect(shooter.getByText('Your turn · Tap an enemy cell', { exact: true })).toBeVisible();
        const xsrf = (await receiver.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
        const resigned = await receiver.request.post(`/api/v1/games/${gameId}/commands`, {
            headers: { 'X-XSRF-TOKEN': xsrf ?? '', Origin: stack.origin },
            data: { commandId: crypto.randomUUID(), command: { type: 'RESIGN' } },
        });
        expect(resigned.ok()).toBeTruthy();
        const refused = shooter.waitForResponse((response) => response.url().endsWith('/commands'));
        await shooter
            .getByRole('grid', { name: 'Enemy waters' })
            .getByRole('gridcell', { name: 'J10, unknown', exact: true })
            .click();
        expect((await refused).status()).toBe(409);
        await expect(shooter.getByRole('heading', { name: 'Match in progress', includeHidden: true })).toHaveCount(0);
        await expect(shooter.getByRole('alert')).toContainText('That action is no longer available.');
        expect((await readGame(shooter, gameId)).phase).toBe('FINISHED');
    } finally {
        await guest.close();
    }
});

test('mobile Pulse shows each result before a turn handoff and respects manual selection', async ({
    page,
    browser,
    useStack,
}) => {
    const stack = await useStack('pulse-play', { frontendPort: 4195, backendPort: 8195 });
    const { guest, shooter, receiver } = await startGame(page, browser, stack.origin);
    try {
        for (const player of [shooter, receiver]) {
            await player.setViewportSize({ width: 390, height: 844 });
            await player.reload();
        }
        const tab = (player: Page, name: string) => player.getByRole('tab', { name, exact: true });
        await expect(tab(shooter, 'Enemy waters')).toHaveAttribute('aria-selected', 'true');
        await expect(tab(receiver, 'Your fleet')).toHaveAttribute('aria-selected', 'true');
        await fire(shooter, 'A1');
        await expect(shooter.getByRole('gridcell', { name: 'A1, hit', exact: true })).toHaveAttribute(
            'data-last-shot',
            'true',
        );
        await shooter.waitForTimeout(1100);
        await expect(tab(shooter, 'Enemy waters')).toHaveAttribute('aria-selected', 'true');
        await fire(shooter, 'J10');
        await expect(receiver.getByRole('gridcell', { name: 'J10, miss', exact: true })).toHaveAttribute(
            'data-last-shot',
            'true',
        );
        await shooter.waitForTimeout(400);
        await expect(tab(shooter, 'Enemy waters')).toHaveAttribute('aria-selected', 'true');
        await expect(tab(receiver, 'Your fleet')).toHaveAttribute('aria-selected', 'true');
        await expect(tab(shooter, 'Your fleet')).toHaveAttribute('aria-selected', 'true');
        await expect(tab(receiver, 'Enemy waters')).toHaveAttribute('aria-selected', 'true');
        await fire(receiver, 'J10');
        await expect(tab(receiver, 'Enemy waters')).toHaveAttribute('aria-selected', 'true');
        await tab(receiver, 'Your fleet').click();
        await tab(receiver, 'Enemy waters').click();
        await receiver.waitForTimeout(1100);
        await expect(tab(receiver, 'Enemy waters')).toHaveAttribute('aria-selected', 'true');
        await expect(tab(shooter, 'Enemy waters')).toHaveAttribute('aria-selected', 'true');
        await receiver.setViewportSize({ width: 1280, height: 800 });
        await expect(tab(receiver, 'Your fleet')).toBeHidden();
        await receiver.evaluate(
            () =>
                new Promise<void>((resolve) => {
                    requestAnimationFrame(() => {
                        requestAnimationFrame(() => {
                            resolve();
                        });
                    });
                }),
        );
        await expect(receiver.getByRole('grid', { name: 'Your fleet' })).toBeVisible();
        await expect(receiver.getByRole('grid', { name: 'Enemy waters' })).toBeVisible();
        await receiver.setViewportSize({ width: 390, height: 844 });
        await expect(tab(receiver, 'Your fleet')).toHaveAttribute('aria-selected', 'true');
        await receiver.reload();
        await expect(tab(receiver, 'Your fleet')).toHaveAttribute('aria-selected', 'true');
        await expect(receiver.locator('[data-last-shot="true"]')).toHaveCount(0);
        await receiver.setViewportSize({ width: 844, height: 390 });
        await expect(receiver.getByRole('grid', { name: 'Your fleet' })).toBeVisible();
        await expect(receiver.getByRole('grid', { name: 'Enemy waters' })).toBeVisible();
    } finally {
        await guest.close();
        await stack.stop();
    }
});

test('a newer shot supersedes a pending handoff and ending the game cancels it', async ({
    page,
    browser,
    useStack,
}) => {
    const stack = await useStack('pulse-race', { frontendPort: 4197, backendPort: 8197 });
    const { guest, gameId, shooter, receiver } = await startGame(page, browser, stack.origin);
    try {
        for (const player of [shooter, receiver]) {
            await player.setViewportSize({ width: 390, height: 844 });
            await player.reload();
        }
        const tab = (player: Page, name: string) => player.getByRole('tab', { name, exact: true });
        await expect(tab(receiver, 'Your fleet')).toHaveAttribute('aria-selected', 'true');
        await fire(shooter, 'J10');
        await tab(receiver, 'Enemy waters').click();
        await fire(receiver, 'J10');
        await shooter.waitForTimeout(1200);
        await expect(tab(shooter, 'Enemy waters')).toHaveAttribute('aria-selected', 'true');
        await expect(tab(receiver, 'Your fleet')).toHaveAttribute('aria-selected', 'true');
        await fire(shooter, 'I10');
        const xsrf = (await receiver.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
        const resigned = await receiver.request.post(`${stack.origin}/api/v1/games/${gameId}/commands`, {
            headers: { 'X-XSRF-TOKEN': xsrf ?? '', Origin: stack.origin },
            data: { commandId: crypto.randomUUID(), command: { type: 'RESIGN' } },
        });
        expect(resigned.ok()).toBeTruthy();
        await expect(shooter.getByRole('heading', { name: 'Victory', exact: true })).toBeVisible();
        await shooter.waitForTimeout(1200);
        await expect(shooter.getByRole('heading', { name: 'Victory', exact: true })).toBeVisible();
        await expect(shooter.getByRole('tablist', { name: 'Choose board' })).toHaveCount(0);
    } finally {
        await guest.close();
        await stack.stop();
    }
});

test('reconnecting after a missed shot selects the current turn without replaying Pulse', async ({
    page,
    browser,
    useStack,
}) => {
    const stack = await useStack('pulse-reconnect', { frontendPort: 4194, backendPort: 8194 });
    const { guest, shooter, receiver } = await startGame(page, browser, stack.origin);
    try {
        for (const player of [shooter, receiver]) {
            await player.setViewportSize({ width: 390, height: 844 });
            await player.reload();
        }
        await expect(receiver.getByRole('tab', { name: 'Your fleet', exact: true })).toHaveAttribute(
            'aria-selected',
            'true',
        );
        await receiver.context().setOffline(true);
        await expect(receiver.getByText('Offline', { exact: true })).toBeVisible();
        await fire(shooter, 'J10');
        await receiver.context().setOffline(false);
        await expect(receiver.getByText('Connected', { exact: true })).toBeVisible({ timeout: 10000 });
        await expect(receiver.getByText('Your turn · Tap an enemy cell', { exact: true })).toBeVisible();
        await expect(receiver.getByRole('tab', { name: 'Enemy waters', exact: true })).toHaveAttribute(
            'aria-selected',
            'true',
        );
        await expect(receiver.locator('[data-last-shot="true"]')).toHaveCount(0);
        await receiver.waitForTimeout(1100);
        await expect(receiver.getByRole('tab', { name: 'Enemy waters', exact: true })).toHaveAttribute(
            'aria-selected',
            'true',
        );
    } finally {
        await receiver.context().setOffline(false);
        await guest.close();
        await stack.stop();
    }
});
