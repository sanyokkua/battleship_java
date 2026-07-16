import { test, expect } from '@playwright/test';
import {
  readPersistedSession,
  createOpponent,
  fetchShipCatalog,
  coordinateLabelPattern,
  persistStage,
  hardNavigate,
} from './support/mockBackdoor';

/**
 * Proves PreparationScreen's own client-side pre-validation (validatePlacement in
 * screens/PreparationScreen.tsx) rejects a placement whose footprint intrudes into
 * an already-placed ship's no-go moat - *before* any adapter call is made - and
 * surfaces it as an error toast, leaving the ship tray unchanged.
 *
 * Geometry (Ukrainian edition, largest ship first from the tray - size 4):
 *   1. Ship A: HORIZONTAL, anchor (row 5, col C) -> occupies row 5, columns C..C+3.
 *      Its moat covers rows 4-6 and columns (C-1)..(C+4).
 *   2. Ship B: anchor (row 3, col C+4) is *outside* the moat (row 3 isn't touched by
 *      a ship confined to rows 4-6) and so is clickable, but placed VERTICAL its
 *      second cell lands at (row 4, col C+4) - inside Ship A's moat (one column past
 *      its right edge) - triggering the 'tooClose' rejection. Neither of Ship B's
 *      first two cells is ever one of Ship A's own occupied cells (those stop at
 *      column C+3), so the rejection reason is specifically "too close", not
 *      "occupied".
 */
test('a rejected placement (ships too close) shows an error toast and leaves the tray unchanged', async ({ page }) => {
  test.setTimeout(45000);
  await page.goto('/');

  await page.getByRole('button', { name: 'New Game' }).click();
  await page.getByRole('radio', { name: /Ukrainian/i }).click();
  await page.getByLabel('Player name').fill('Rejects');
  await page.getByRole('button', { name: 'Start new game' }).click();
  await expect(page).toHaveURL(/\/game\/wait$/);

  const { sessionId, player } = await readPersistedSession(page);
  await createOpponent(page, sessionId, 'Bystander');

  // Move on to Preparation. (The app's own poll-driven Wait -> Preparation
  // auto-navigation is currently broken by a pre-existing bug - see
  // mockBackdoor.ts's module doc comment. hardNavigate is this ticket's
  // in-scope workaround.)
  await persistStage(page, 'PREPARATION');
  await hardNavigate(page, '/game/preparation');
  await expect(page.getByText('Place your fleet')).toBeVisible();

  const ships = await fetchShipCatalog(page, sessionId, player!.playerId);
  const totalShips = ships.length;
  const largestSize = ships[ships.length - 1].shipSize; // tray shows largest-unplaced first

  const shipAAnchor = { row: 5, column: 2 };
  const shipBAnchor = { row: 3, column: shipAAnchor.column + largestSize };

  const tray = page.locator('.fleet-panel');
  const unplacedButtons = tray.locator('button.ship-item');
  const board = page.locator('.board-panel .board');

  await expect(unplacedButtons).toHaveCount(totalShips);

  // Ship A: place successfully (default direction is HORIZONTAL).
  await unplacedButtons.first().click();
  await board.getByRole('button', { name: coordinateLabelPattern(shipAAnchor) }).click();
  await expect(unplacedButtons).toHaveCount(totalShips - 1);

  // Ship B: select the next (largest remaining) ship, switch to VERTICAL, then click
  // an anchor whose footprint intrudes into Ship A's moat - expect a client-side
  // rejection (no adapter call), an error toast, and no change to the tray.
  await unplacedButtons.first().click();
  await page.getByRole('button', { name: /Vertical/i }).click();
  await board.getByRole('button', { name: coordinateLabelPattern(shipBAnchor) }).click();

  const errorToast = page.getByRole('alert');
  await expect(errorToast).toBeVisible();
  await expect(errorToast).toContainText("Ships can't touch");

  await expect(unplacedButtons).toHaveCount(totalShips - 1);
});
