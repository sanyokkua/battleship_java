import { test, expect, type Page } from '@playwright/test';
import {
  readPersistedSession,
  createOpponent,
  placeFullFleetAndReady,
  persistStage,
  hardNavigate,
} from './support/mockBackdoor';

/**
 * Fast-seeds a fresh page all the way to IN_GAME using minimal real UI (just enough
 * to get Player 1's session/player into localStorage) plus the mock-adapter backdoor
 * for everything else (both fleets placed, both players readied). Reaches
 * `/game/gameplay` via `hardNavigate` rather than the app's own poll-driven
 * auto-navigation, which currently cannot get there at all - see mockBackdoor.ts's
 * module doc comment for the pre-existing bug this works around.
 */
async function seedToInGame(page: Page): Promise<void> {
  await page.goto('/');
  await page.getByRole('button', { name: 'New Game' }).click();
  await page.getByRole('radio', { name: /Ukrainian/i }).click();
  await page.getByLabel('Player name').fill('Grace');
  await page.getByRole('button', { name: 'Start new game' }).click();
  await expect(page).toHaveURL(/\/game\/wait$/);

  const { sessionId, player } = await readPersistedSession(page);
  const opponent = await createOpponent(page, sessionId, 'Rival');
  await placeFullFleetAndReady(page, sessionId, player!.playerId);
  await placeFullFleetAndReady(page, sessionId, opponent.playerId);

  await persistStage(page, 'IN_GAME');
  await hardNavigate(page, '/game/gameplay');
  await expect(page.locator('.scoreboard')).toBeVisible();
}

test.describe('responsive layout: gameplay boards', () => {
  test('mobile viewport (<=640px) shows a single board with a tab switch', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 375, height: 812 });
    await seedToInGame(page);

    const tabs = page.getByRole('tablist');
    await expect(tabs).toBeVisible();

    const targetTab = page.getByRole('tab', { name: 'Target grid' });
    const fleetTab = page.getByRole('tab', { name: 'Your fleet' });
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

  test('desktop viewport (>=641px) shows both boards side-by-side with no tab control', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1280, height: 800 });
    await seedToInGame(page);

    await expect(page.getByRole('tablist')).toBeHidden();

    const targetPanel = page.locator('.bp-target');
    const fleetPanel = page.locator('.bp-fleet');
    await expect(targetPanel).toBeVisible();
    await expect(fleetPanel).toBeVisible();
  });
});
