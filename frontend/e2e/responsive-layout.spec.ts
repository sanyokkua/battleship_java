import {expect, type Page, test} from '@playwright/test';
import type {ResponseCreatedPlayerDto} from '../src/logic/ApplicationTypes';
import {
  createOpponent,
  hardNavigate,
  persistStage,
  placeFullFleetAndReady,
  readPersistedSession,
  shootViaBackdoor,
} from './support/mockBackdoor';

/**
 * Fast-seeds a fresh page all the way to IN_GAME using minimal real UI (just enough
 * to get Player 1's session/player into localStorage) plus the mock-adapter backdoor
 * for everything else (both fleets placed, both players readied). Reaches
 * `/game/gameplay` via `hardNavigate` rather than the app's own poll-driven
 * auto-navigation, which currently cannot get there at all - see mockBackdoor.ts's
 * module doc comment for the pre-existing bug this works around.
 */
async function seedToInGame(
    page: Page,
    opponentName = 'Rival',
): Promise<{ sessionId: string; player: ResponseCreatedPlayerDto; opponent: ResponseCreatedPlayerDto }> {
    await page.goto('/');
    await page.getByRole('button', {name: 'New Game'}).click();
    await page.getByRole('radio', {name: /Ukrainian/i}).click();
    await page.getByLabel('Player name').fill('Grace');
    await page.getByRole('button', {name: 'Start new game'}).click();
    await expect(page).toHaveURL(/\/game\/wait$/);

    const {sessionId, player} = await readPersistedSession(page);
    const opponent = await createOpponent(page, sessionId, opponentName);
    await placeFullFleetAndReady(page, sessionId, player!.playerId);
    await placeFullFleetAndReady(page, sessionId, opponent.playerId);

    await persistStage(page, 'IN_GAME');
    await hardNavigate(page, '/game/gameplay');
    await expect(page.locator('.scoreboard')).toBeVisible();

    return {sessionId, player: player!, opponent};
}

test.describe('responsive layout: gameplay boards', () => {
    test('mobile viewport (<=640px) shows a single board with a tab switch', async ({page}) => {
        test.setTimeout(60000);
        await page.setViewportSize({width: 375, height: 812});
        await seedToInGame(page);

        const tabs = page.getByRole('tablist');
        await expect(tabs).toBeVisible();

        const targetTab = page.getByRole('tab', {name: 'Target grid'});
        const fleetTab = page.getByRole('tab', {name: 'Your fleet'});
        const targetPanel = page.locator('.bp-target');
        const fleetPanel = page.locator('.bp-fleet');

        // Target tab is active by default: target panel visible, fleet panel hidden.
        await expect(targetTab).toHaveAttribute('aria-selected', 'true');
        await expect(targetPanel).toBeVisible();
        await expect(fleetPanel).toBeHidden();

        // Switching tabs toggles which single board is visible.
        await fleetTab.click();
        await expect(fleetTab).toHaveAttribute('aria-selected', 'true');
        await expect(fleetPanel).toBeVisible();
        await expect(targetPanel).toBeHidden();

        await targetTab.click();
        await expect(targetTab).toHaveAttribute('aria-selected', 'true');
        await expect(targetPanel).toBeVisible();
        await expect(fleetPanel).toBeHidden();
    });

    test('mobile viewport: turn-flip auto-switch does not shift the visible board or scroll position', async ({
                                                                                                                  page,
                                                                                                              }) => {
        test.setTimeout(60000);
        await page.setViewportSize({width: 375, height: 812});
        // Both board panels stay permanently mounted (crossfade design), so the grid cell they
        // share is always sized to the taller of the two regardless of which is visible - that
        // part of the layout can't reflow from the switch itself. What CAN reflow is TurnBanner:
        // its "their turn" text embeds the opponent's name (its "your turn" text doesn't), so a
        // long name is needed to make that text wrap to a second line and grow the banner - and,
        // without a reserved min-height, shift everything below it (including the boards) on
        // every turn flip.
        const {sessionId, player, opponent} = await seedToInGame(page, 'Anastasiia-Konstantinopoulos-Zhurbenko');

        // Scroll the boards into view, mimicking a real mobile user looking at the board when a
        // turn flips.
        await page.locator('.boards-area').scrollIntoViewIfNeeded();

        // Row 9 is never touched by computeFleetLayout's row-pair placement (ships only occupy
        // rows 0/2/4/6/8), so it's a guaranteed miss on both boards before any ship is sunk.
        await shootViaBackdoor(page, sessionId, player.playerId, {row: 9, column: 0});
        await expect(page.locator('.bp-fleet')).toBeVisible();
        await expect(page.locator('.bp-target')).toBeHidden();

        const scrollYBeforeFlip = await page.evaluate(() => window.scrollY);
        const fleetTopBeforeFlip = await page.locator('.bp-fleet').evaluate((el) => el.getBoundingClientRect().top);

        // Opponent's shot also misses, flipping the turn back to the player and triggering the
        // SWITCH_DELAY_MS-delayed auto-switch back to the target tab.
        await shootViaBackdoor(page, sessionId, opponent.playerId, {row: 9, column: 0});

        await expect(page.locator('.bp-target')).toBeVisible({timeout: 5000});
        await expect(page.locator('.bp-fleet')).toBeHidden();

        const targetTopAfterFlip = await page.locator('.bp-target').evaluate((el) => el.getBoundingClientRect().top);
        const scrollYAfterFlip = await page.evaluate(() => window.scrollY);

        expect(targetTopAfterFlip).toBe(fleetTopBeforeFlip);
        expect(scrollYAfterFlip).toBe(scrollYBeforeFlip);
    });

    test('desktop viewport (>=641px) shows both boards side-by-side with no tab control', async ({page}) => {
        test.setTimeout(60000);
        await page.setViewportSize({width: 1280, height: 800});
        await seedToInGame(page);

        await expect(page.getByRole('tablist')).toBeHidden();

        const targetPanel = page.locator('.bp-target');
        const fleetPanel = page.locator('.bp-fleet');
        await expect(targetPanel).toBeVisible();
        await expect(fleetPanel).toBeVisible();
    });
});
