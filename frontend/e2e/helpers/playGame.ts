import { expect, type Browser, type Page } from '@playwright/test';
import type { components } from '../../src/contract/generated/api';

export type Snapshot = components['schemas']['GameSnapshot'];

export async function readGame(page: Page, gameId: string): Promise<Snapshot> {
    const response = await page.request.get(`/api/v1/games/${gameId}`);
    expect(response.ok()).toBeTruthy();
    return (await response.json()) as Snapshot;
}

export async function prepareGame(page: Page, browser: Browser, origin: string, rulesetName: string) {
    await page.goto(`${origin}/create`);
    await page.getByRole('radio').filter({ hasText: rulesetName }).click();
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
    for (const player of [page, guest]) {
        await expect(player.getByRole('grid', { name: 'Your sea' })).toBeVisible();
        const randomized = player.waitForResponse((response) => response.url().endsWith('/commands'));
        await player.getByRole('button', { name: 'Random', exact: true }).click();
        expect((await randomized).ok()).toBeTruthy();
        await player.getByRole('button', { name: 'Ready', exact: true }).click();
        const ready = player.waitForResponse((response) => response.url().endsWith('/commands'));
        await player.getByRole('button', { name: 'Commit and wait', exact: true }).click();
        expect((await ready).ok()).toBeTruthy();
    }
    await expect(page.getByRole('grid', { name: 'Enemy waters' })).toBeVisible();
    await expect(guest.getByRole('grid', { name: 'Enemy waters' })).toBeVisible();
    return { guest, gameId: snapshot.gameId };
}

// Only the acting player's visible turn and unknown target controls choose each shot.
export async function playGame(page: Page, browser: Browser, origin: string, rulesetName: string) {
    const game = await prepareGame(page, browser, origin, rulesetName);
    const players = [page, game.guest];
    for (let shots = 0; shots < 200; shots++) {
        let shooter: Page | undefined;
        await expect
            .poll(async () => {
                for (const player of players) {
                    if (await player.getByText('Your turn · Tap an enemy cell', { exact: true }).isVisible()) {
                        shooter = player;
                        return true;
                    }
                }
                return false;
            })
            .toBeTruthy();
        if (!shooter) throw new Error('No player can fire');
        const response = shooter.waitForResponse((entry) => entry.url().endsWith('/commands'));
        const target = shooter
            .getByRole('grid', { name: 'Enemy waters' })
            .getByRole('gridcell', { name: /, unknown$/ })
            .first();
        const targetName = await target.getAttribute('aria-label');
        await target.click();
        const accepted = await response;
        expect(accepted.ok()).toBeTruthy();
        const snapshot = (await accepted.json()) as Snapshot;
        if (snapshot.phase === 'FINISHED') return game;
        if (!targetName) throw new Error('The target has no coordinate label');
        await expect(shooter.getByRole('gridcell', { name: targetName, exact: true })).toHaveCount(0);
    }
    throw new Error('The game did not finish after both boards were explored');
}
