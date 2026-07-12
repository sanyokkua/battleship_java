import { test, expect } from '@playwright/test';
import { readPersistedSession, createOpponent, persistStage, hardNavigate } from './support/mockBackdoor';

/**
 * AppBar's own session guard (useSessionGuard, see hooks/useSessionGuard.ts) reads
 * localStorage exactly once, on mount - by design, it is not a reactive subscription
 * (see that hook's doc comment, and AppBar.test.tsx, which always seeds localStorage
 * *before* rendering AppBar). AppBar itself is mounted once for the app's lifetime
 * (it lives outside <AppRoutes/>, so client-side route changes never remount it) - so
 * within one continuous in-app session, AppBar never notices a stage that changed
 * *after* it first mounted, and the leave-confirmation gate would never trigger.
 *
 * That matches a real scenario: the confirmation only ever fires for a browser tab
 * that mounts fresh *while* a mid-game session is already sitting in localStorage
 * (e.g. the player reloads/reopens the tab mid-game). This test reproduces exactly
 * that: reach PREPARATION via the real UI + backdoor opponent (same as
 * placement-rejected-toast.spec.ts, including the hardNavigate workaround documented
 * in mockBackdoor.ts for the *separate*, pre-existing StageGuard bug), then reload so
 * AppBar mounts fresh against the now-persisted PREPARATION stage. The reload also
 * resets the mock adapter's in-memory session (a fresh page load = a fresh
 * MockGameAdapter instance), but this spec only needs the *persisted* session/stage
 * data and the AppBar/dialog UI from that point on - no further live game calls are
 * made.
 */
test('leaving a mid-game session prompts a focus-trapped confirmation, and only navigates on confirm', async ({
  page,
}) => {
  test.setTimeout(45000);
  await page.goto('/');

  await page.getByRole('button', { name: 'New Game' }).click();
  await page.getByRole('radio', { name: /Ukrainian/i }).click();
  await page.getByLabel('Player name').fill('Leaver');
  await page.getByRole('button', { name: 'Start new game' }).click();
  await expect(page).toHaveURL(/\/game\/wait$/);

  const { sessionId } = await readPersistedSession(page);
  await createOpponent(page, sessionId, 'Stayer');

  await persistStage(page, 'PREPARATION');
  await hardNavigate(page, '/game/preparation');
  await expect(page).toHaveURL(/\/game\/preparation$/);

  // Force AppBar to (re)mount against the now-persisted PREPARATION stage - see the
  // doc comment above for why this is necessary.
  await page.reload();
  await expect(page).toHaveURL(/\/game\/preparation$/);

  // AppBar now recognizes an active mid-game session.
  await expect(page.getByRole('link', { name: 'Preparation' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Gameplay' })).toBeVisible();

  const dialog = page.getByRole('dialog');

  // Clicking a nav link mid-game opens a focus-trapped confirmation instead of navigating.
  await page.getByRole('link', { name: 'Home' }).click();
  await expect(dialog).toBeVisible();
  await expect(dialog).toHaveAttribute('aria-modal', 'true');
  await expect(page.getByRole('button', { name: 'Cancel' })).toBeFocused();

  // Escape closes it without navigating; the session stays intact.
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(page).toHaveURL(/\/game\/preparation$/);
  expect(await page.evaluate(() => localStorage.getItem('session_str'))).not.toBeNull();

  // Re-trigger, then confirm: navigation happens and all persisted game data is cleared.
  await page.getByRole('link', { name: 'Home' }).click();
  await expect(dialog).toBeVisible();
  await page.getByRole('button', { name: 'Leave game' }).click();

  await expect(dialog).toHaveCount(0);
  await expect(page).toHaveURL(/\/$/);
  expect(await page.evaluate(() => localStorage.getItem('session_str'))).toBeNull();
  expect(await page.evaluate(() => localStorage.getItem('player_obj'))).toBeNull();
  expect(await page.evaluate(() => localStorage.getItem('gameStage_str'))).toBeNull();
});
