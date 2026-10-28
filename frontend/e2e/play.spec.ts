import { expect, test } from './fixtures/stack.js';
import type { Browser, Page } from '@playwright/test';
import type { components } from '../src/contract/generated/api';

type Snapshot = components['schemas']['GameSnapshot'];
type CommandRequest = components['schemas']['CommandRequest'];

async function readGame(page: Page, gameId: string): Promise<Snapshot> {
    const response = await page.request.get(`/api/v1/games/${gameId}`);
    expect(response.ok()).toBeTruthy();
    return (await response.json()) as Snapshot;
}

async function fixedFleet(page: Page, classic: boolean) {
    const fleet = classic
        ? [
              ['Carrier', 'A1'],
              ['Battleship', 'A3'],
              ['Destroyer', 'A5'],
              ['Submarine', 'E5'],
              ['Patrol boat', 'A7'],
          ]
        : [
              ['4 cell ship', 'A1'],
              ['3 cell ship', 'A3'],
              ['3 cell ship', 'E3'],
              ['2 cell ship', 'A5'],
              ['2 cell ship', 'D5'],
              ['2 cell ship', 'G5'],
              ['1 cell ship', 'A7'],
              ['1 cell ship', 'C7'],
              ['1 cell ship', 'E7'],
              ['1 cell ship', 'G7'],
          ];
    for (const [ship, anchor] of fleet) {
        await page.getByRole('gridcell', { name: `${anchor}, water`, exact: true }).click();
        await page.getByRole('group', { name: 'Choose ship' }).getByRole('button', { name: ship, exact: true }).click();
        const applied = page.waitForResponse((response) => response.url().endsWith('/commands'));
        await page.getByRole('button', { name: 'Apply', exact: true }).click();
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
        (response) => response.request().method() === 'POST' && response.url().endsWith('/api/v1/games'),
    );
    await page.getByRole('button', { name: 'Create game', exact: true }).click();
    const snapshot = (await (await created).json()) as Snapshot;
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
        expect(await revealed.evaluate((cell) => getComputedStyle(cell).backgroundColor)).not.toBe(
            await miss.evaluate((cell) => getComputedStyle(cell).backgroundColor),
        );
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
        expect(snapshot.opponentBoard.grid.flat().filter((state) => state === 'HIT' || state === 'MISS').length).toBe(
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
