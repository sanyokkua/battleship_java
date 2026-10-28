import { expect, test } from './fixtures/stack.js';
import type { Page } from '@playwright/test';
import { playGame, prepareGame, readGame, type Snapshot } from './helpers/playGame.js';

function duration(value: number | undefined): string {
    if (value === undefined) return '—';
    const seconds = Math.floor(value / 1000);
    return `${String(Math.floor(seconds / 60))}:${String(seconds % 60).padStart(2, '0')}`;
}

function accuracy(value: number | undefined): string {
    return value === undefined ? '—' : `${(value * 100).toFixed(2)}%`;
}

async function expectResults(page: Page, snapshot: Snapshot) {
    const { outcome, statistics } = snapshot;
    if (!outcome || !statistics) throw new Error('Finished result missing outcome or statistics');
    await expect(
        page.getByRole('heading', { name: outcome.winner === 'YOU' ? 'Victory' : 'Defeat', exact: true }),
    ).toBeVisible();
    await expect(
        page.getByText(outcome.reason === 'FLEET_DESTROYED' ? 'Fleet destroyed' : 'Match ended by resignation', {
            exact: true,
        }),
    ).toBeVisible();
    for (const [label, value] of [
        ['Intact', statistics.you.fleet.intact],
        ['Damaged', statistics.you.fleet.damaged],
        ['Sunk', statistics.you.fleet.sunk],
    ] as const) {
        await expect(page.getByRole('group', { name: label, exact: true })).toHaveText(`${String(value)}${label}`);
    }
    const cards = [
        [
            'Match time',
            [
                ['Total match', duration(statistics.match.totalDurationMs)],
                ['Placement phase', duration(statistics.match.placementDurationMs)],
                ['Your placement', duration(statistics.you.placementDurationMs)],
                ['Opponent placement', duration(statistics.opponent.placementDurationMs)],
                ['Gameplay', duration(statistics.match.gameplayDurationMs)],
            ],
        ],
        [
            'Turns & accuracy',
            [
                ['Your turns', String(statistics.you.turns.count)],
                ['Opponent turns', String(statistics.opponent.turns.count)],
                ['Your hits / shots', `${String(statistics.you.hits)} / ${String(statistics.you.shots)}`],
                ['Your accuracy', accuracy(statistics.you.accuracy)],
            ],
        ],
        [
            'Turn timing',
            [
                ['Your total clock', duration(statistics.you.turns.totalMs)],
                ['Opponent total clock', duration(statistics.opponent.turns.totalMs)],
                ['Your average', duration(statistics.you.turns.averageMs)],
                ['Opponent average', duration(statistics.opponent.turns.averageMs)],
                ['Your fastest', duration(statistics.you.turns.fastestMs)],
                ['Your slowest', duration(statistics.you.turns.slowestMs)],
            ],
        ],
    ] as const;
    for (const [card, rows] of cards) {
        const region = page.getByRole('region', { name: card, exact: true });
        for (const [label, value] of rows) {
            await expect(
                region.locator('.stat-row').filter({ has: page.getByText(label, { exact: true }) }),
            ).toHaveText(`${label}${value}`);
        }
    }
    for (const label of ['Enemy waters · Revealed', 'Your fleet']) {
        const board = page.getByRole('grid', { name: label, exact: true });
        await expect(board).toBeVisible();
        await expect(board.getByRole('gridcell', { name: /, unknown$/ })).toHaveCount(0);
        await expect(board.locator('[role="gridcell"][aria-disabled="false"]')).toHaveCount(0);
    }
    await expect(page.getByText('Results available for', { exact: false })).toBeVisible();
    await expect(page.locator('time')).toHaveAttribute('datetime', snapshot.expiresAt);
    await expect(page.getByRole('button', { name: 'Create another game', exact: true })).toBeVisible();
}

test.setTimeout(180_000);

// Removing results routing, any supplied statistics row, revealed boards, or leave access revocation fails here.
for (const rulesetName of ['Sea Battle', 'Battleship Classic 2002']) {
    test(`${rulesetName} full UI game reveals both server results and Leave results ends access`, async ({
        page,
        browser,
        stack,
    }) => {
        const { guest, gameId } = await playGame(page, browser, stack.origin, rulesetName);
        try {
            for (const player of [page, guest]) await expectResults(player, await readGame(player, gameId));
            const left = page.waitForResponse((response) => response.url().endsWith('/leave'));
            await page.getByRole('button', { name: 'Leave results', exact: true }).click();
            expect((await left).status()).toBe(204);
            await expect(page.getByRole('heading', { name: 'Naval command center', exact: true })).toBeVisible();
            expect(await page.evaluate(() => localStorage.getItem('battleship.currentGameId'))).toBeNull();
            expect((await page.request.get(`/api/v1/games/${gameId}`)).status()).toBe(404);
        } finally {
            await guest.close();
        }
    });
}

// A result before either player's first shot keeps absent accuracy and empty timing aggregates as em dashes.
test('a zero-shot result renders absent optional statistics and accepts an already-left result', async ({
    page,
    browser,
    stack,
}) => {
    const { guest, gameId } = await prepareGame(page, browser, stack.origin, 'Sea Battle');
    try {
        const xsrf = (await guest.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
        const resigned = await guest.request.post(`/api/v1/games/${gameId}/commands`, {
            headers: { 'X-XSRF-TOKEN': xsrf ?? '', Origin: stack.origin },
            data: { commandId: crypto.randomUUID(), command: { type: 'RESIGN' } },
        });
        expect(resigned.ok()).toBeTruthy();
        for (const player of [page, guest]) await expectResults(player, await readGame(player, gameId));
        await page.route('**/events', (route) => route.abort());
        await page.reload();
        await expect(page.getByRole('heading', { name: 'Victory', exact: true })).toBeVisible();
        const hostXsrf = (await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
        const left = await page.request.post(`/api/v1/games/${gameId}/leave`, {
            headers: { 'X-XSRF-TOKEN': hostXsrf ?? '', Origin: stack.origin },
        });
        expect(left.status()).toBe(204);
        const repeated = page.waitForResponse((response) => response.url().endsWith('/leave'));
        await page.getByRole('button', { name: 'Leave results', exact: true }).click();
        expect((await repeated).status()).toBe(404);
        await expect(page.getByRole('heading', { name: 'Naval command center', exact: true })).toBeVisible();
    } finally {
        await guest.close();
    }
});
