import { test, expect, type Page } from '@playwright/test';
import type { Coordinate, ShipDto } from '../src/logic/ApplicationTypes';
import { computeFleetLayout, coordinateLabel, coordinateLabelPattern } from '../e2e/support/fleetHelpers';
import { placeFullFleetViaUi } from '../e2e/support/mockBackdoor';

/**
 * Live full-game e2e: two real Playwright BrowserContexts (two independent
 * localStorage stores, exactly like two separate players' browsers) driving the
 * REAL packaged Spring Boot JAR via the real `HttpGameAdapter` — no mock adapter,
 * no `window.__e2eMockHooks` backdoor, no forced navigation. This is the first
 * spec to exercise the full poll-driven auto-navigation chain (Wait -> Preparation
 * -> Gameplay -> Results, all on the same page) end to end against a real server,
 * now that useSessionGuard.ts no longer caches its localStorage read in useState
 * (the fix that made StageGuard's multi-hop auto-navigation trustworthy — see
 * frontend/src/hooks/useSessionGuard.ts's doc comment).
 *
 * `placeFullFleetViaUi` is reused as-is from e2e/support/mockBackdoor.ts: despite
 * living in that file, it never touches window.__e2eMockHooks — it only drives the
 * real ShipTray/DirectionToggle/Board UI via page.locator()/getByRole() clicks, so
 * it's exactly as valid against the real HttpGameAdapter as it is against the mock
 * one. computeFleetLayout/coordinateLabel/coordinateLabelPattern come from the
 * shared, adapter-agnostic e2e/support/fleetHelpers.ts (see that file's own doc
 * comment for the placement algorithm).
 */

// Ukrainian edition's fixed ship composition (frontend/src/i18n-support/editionCompositions.ts):
// 4x1 + 3x2 + 2x3 + 1x4 = 10 ships / 20 total ship cells. Chosen over Milton Bradley
// (10 ships / 30 cells) specifically to minimize real-network, real-3s/5s-polling
// runtime for this test — fewer shots needed to reach a decisive result.
const UKRAINIAN_SHIP_SIZES = [1, 1, 1, 1, 2, 2, 2, 3, 3, 4];

// computeFleetLayout only needs sizes (in ascending order, matching how the real
// catalog is returned) to compute a valid non-overlapping layout — the shipId it
// assigns is never sent to the server. placeFullFleetViaUi drives placement purely
// through tray-button/direction/board-cell clicks; the server resolves the *real*
// shipId itself from whichever tray ship is currently selected. So a synthetic,
// locally-known catalog is sufficient for computing *where* to click, without
// needing to query the real ship catalog over the network first.
function syntheticUkrainianCatalog(): ShipDto[] {
  return UKRAINIAN_SHIP_SIZES.map((shipSize, index) => ({ shipId: `synthetic-${index}`, shipSize }));
}

/** Reads the localStorage session id the real UI persisted (GameBrowserStorage's "session_str" key). */
async function readSessionId(page: Page): Promise<string> {
  const sessionId = await page.evaluate(() => localStorage.getItem('session_str'));
  if (!sessionId) {
    throw new Error('session_str missing from localStorage after game creation');
  }
  return sessionId;
}

/**
 * Polls (no fixed sleeps beyond the bounded interval below) until either this page's
 * TurnBanner shows "Your turn" or the page has already navigated to /game/results
 * (meaning the game ended without this context ever getting a turn — a legitimate
 * outcome here, since both sides fire only at known ship cells and a HIT/DESTROYED
 * keeps the same player active per the engine's turn rules, so a perfect-information
 * shooter who never misses can in principle sink the whole enemy fleet without the
 * opponent ever firing a shot).
 */
async function waitForMyTurnOrGameOver(page: Page): Promise<'my-turn' | 'game-over'> {
  const yourTurnText = page.getByText(/Your turn/i);
  for (;;) {
    if (/\/game\/results$/.test(page.url())) {
      return 'game-over';
    }
    if (await yourTurnText.isVisible().catch(() => false)) {
      return 'my-turn';
    }
    await page.waitForTimeout(500);
  }
}

/**
 * Fires shots at `targetCells` (the opponent's known ship-cell coordinates) one at a
 * time via real UI clicks on the `.bp-target` board, only ever clicking when the
 * TurnBanner confirms it's actually this context's turn (server-authoritative — see
 * waitForMyTurnOrGameOver). Stops early if the game ends (this context's own 5s
 * gameplay poll observes `hasWinner` and GameplayScreen auto-navigates to
 * /game/results) before all cells are exhausted — see this function's doc comment on
 * why one side may never get a turn at all.
 *
 * Retries a cell (re-checking turn state, not advancing to the next cell) if the
 * click doesn't resolve to "hit"/"sunk" within a short window: the client-side
 * TurnBanner reflects this page's last poll, which can be briefly stale relative to
 * the server's authoritative turn state (e.g. right after both players ready up, or
 * after any other transient client/server desync) - a shot fired on stale
 * information is simply rejected server-side and leaves the cell as "water" rather
 * than corrupting game state, so retrying is safe and correct here.
 */
async function fireAllKnownShots(page: Page, targetCells: Coordinate[]): Promise<void> {
  const targetBoard = page.locator('.bp-target .board');

  for (const cell of targetCells) {
    // Role-agnostic on purpose: once a target-mode cell resolves to hit/sunk, the
    // now-already-shot-cell click guard (BoardCell.tsx) renders it as a
    // non-interactive <div>, not a <button> - so this must NOT be scoped to
    // getByRole('button', ...), or a resolved cell would never match.
    const resolvedCell = targetBoard.locator(
      `[aria-label="${coordinateLabel(cell)}, hit"], [aria-label="${coordinateLabel(cell)}, sunk"]`,
    );

    let resolved = false;

    while (!resolved) {
      // A previous pass through this loop may have landed a successful shot that
      // this loop failed to observe within the window below (client poll lag) -
      // check before attempting another click. Skipping this check would mean
      // clicking a vanished <button> (see above), which Playwright's click()
      // would wait on for the rest of the test's timeout budget instead of
      // failing fast.
      if (await resolvedCell.isVisible().catch(() => false)) {
        break;
      }

      const outcome = await waitForMyTurnOrGameOver(page);
      if (outcome === 'game-over') {
        return;
      }

      const clickTarget = targetBoard.getByRole('button', { name: coordinateLabelPattern(cell) });
      if (await clickTarget.isVisible().catch(() => false)) {
        await clickTarget.click();
      }

      // Every target cell is a known ship cell, so the only two possible resulting
      // states from a shot that was actually accepted are "hit" (ship not yet fully
      // sunk) or "sunk" (this shot finished it). If neither appears within the
      // window (a real, auto-retrying wait via expect().toBeVisible() - NOT a bare
      // isVisible() check, which returns immediately without polling), the click
      // was likely rejected (stale turn info) - loop back and re-check.
      resolved = await expect(resolvedCell)
        .toBeVisible({ timeout: 3000 })
        .then(() => true)
        .catch(() => false);
    }
  }
}

test('live full game: create -> wait -> prepare -> play -> results', async ({ browser }) => {
  // Real network + real 3s (wait/preparation) and 5s (gameplay) polling, repeated
  // across two stage transitions and up to 20 shots, comfortably needs more than
  // Playwright's default 30s test timeout.
  test.setTimeout(5 * 60 * 1000);

  const contextA = await browser.newContext();
  const contextB = await browser.newContext();
  const pageA = await contextA.newPage();
  const pageB = await contextB.newPage();

  try {
    let sessionId = '';

    await test.step('Player A creates a new Ukrainian game', async () => {
      await pageA.goto('/');
      await pageA.getByRole('button', { name: 'New Game' }).click();
      await expect(pageA).toHaveURL(/\/new$/);

      await expect(pageA.getByRole('radio', { name: /Ukrainian/i })).toBeVisible();
      await pageA.getByRole('radio', { name: /Ukrainian/i }).click();
      await pageA.getByLabel('Player name').fill('Ada');
      await pageA.getByRole('button', { name: 'Start new game' }).click();

      await expect(pageA).toHaveURL(/\/game\/wait$/);
      sessionId = await readSessionId(pageA);
      expect(sessionId).toBeTruthy();
    });

    await test.step('Player B joins the game', async () => {
      await pageB.goto('/');
      await pageB.getByRole('button', { name: 'Join Game' }).click();
      await expect(pageB).toHaveURL(/\/join$/);

      await pageB.getByLabel('Player name').fill('Bandit');
      await pageB.getByLabel('Game ID').fill(sessionId);
      await pageB.getByRole('button', { name: 'Join the battle' }).click();

      // B is the second player to join this session, so the server flips the
      // session's stage to PREPARATION synchronously as part of this same
      // createPlayer call (GameImpl.createPlayer: stage becomes PREPARATION the
      // instant a 2nd player is added - see
      // src/main/java/.../logic/engine/GameImpl.java). JoinGameScreen's own
      // getStage() call (issued right after createPlayer resolves) therefore
      // deterministically already observes PREPARATION, not WAITING_FOR_PLAYERS -
      // so, unlike Player A, B never durably lands on /game/wait; StageGuard
      // redirects it straight through to /game/preparation. Asserting B settles on
      // /game/wait would be racing an inherently transient URL (real app behavior,
      // not a bug), so this only waits for either - the very next step then
      // confirms both players land on /game/preparation.
      await pageB.waitForURL(/\/game\/(wait|preparation)$/, { timeout: 15000 });
    });

    await test.step('Both players naturally arrive at Preparation', async () => {
      // Player A is genuinely alone until B joins, so A only reaches Preparation
      // via WaitScreen's real, poll-driven (3s) transition once it observes a
      // second player - no force-navigation. This is the first real exercise of
      // the routing fix. Player B (previous step) may already be there.
      await pageA.waitForURL(/\/game\/preparation$/, { timeout: 30000 });
      await pageB.waitForURL(/\/game\/preparation$/, { timeout: 30000 });
      await expect(pageA.getByText('Place your fleet')).toBeVisible();
      await expect(pageB.getByText('Place your fleet')).toBeVisible();
    });

    const placements = computeFleetLayout(syntheticUkrainianCatalog(), { verticalFirst: true });
    expect(placements.some((p) => p.direction === 'VERTICAL')).toBe(true);
    expect(placements.some((p) => p.direction === 'HORIZONTAL')).toBe(true);

    await test.step('Both players place their full fleet via real UI clicks', async () => {
      await Promise.all([
        placeFullFleetViaUi(pageA, placements, { horizontal: /Horizontal/i, vertical: /Vertical/i }),
        placeFullFleetViaUi(pageB, placements, { horizontal: /Horizontal/i, vertical: /Vertical/i }),
      ]);

      const readyA = pageA.getByRole('button', { name: /Ready to go!/ });
      const readyB = pageB.getByRole('button', { name: /Ready to go!/ });
      await expect(readyA).toBeEnabled();
      await expect(readyB).toBeEnabled();

      // Click A's "Ready" first, then wait for B's own (real, 3s-polled) opponent
      // status to observe A as ready before clicking B's "Ready" - rather than
      // firing both clicks back-to-back. A UI click only waits for the DOM click
      // event to dispatch, not for the resulting async setReady() call to reach the
      // server, so two near-simultaneous clicks can race two concurrent POST
      // .../start requests. The engine's changePlayerStatusToReady (GameImpl.java)
      // reads-then-writes both players' `active`/`ready` flags non-atomically across
      // that pair of requests (frozen game-engine code - out of scope to change here
      // per this repo's redesign ground rules), so a genuine concurrent race there
      // can leave both players marked active at once, which then desyncs the
      // turn-order-aware shot loop below (a shot fired while not *actually* active
      // gets rejected server-side and never turns "water" into "hit"/"sunk").
      // Waiting for this natural poll-driven confirmation first serializes the two
      // ready calls and avoids that race entirely - no force-navigation involved.
      await readyA.click();
      await expect(pageB.getByText(/Opponent:\s*Ready/)).toBeVisible({ timeout: 10000 });
      await readyB.click();
    });

    await test.step('Both players naturally auto-navigate Preparation -> Gameplay', async () => {
      // Real, poll-driven transition (PreparationScreen's post-ready 3s stage watch)
      // - no force-navigation.
      await pageA.waitForURL(/\/game\/gameplay$/, { timeout: 30000 });
      await pageB.waitForURL(/\/game\/gameplay$/, { timeout: 30000 });
      await expect(pageA.locator('.scoreboard')).toBeVisible();
      await expect(pageB.locator('.scoreboard')).toBeVisible();
    });

    // Both fleets share the exact same computed layout (same synthetic catalog on
    // both sides), so each side's "opponent's known ship cells" is this same list.
    const knownShipCells = placements.flatMap((p) => p.cells);

    await test.step('Alternating, turn-order-aware shots until one side wins', async () => {
      await Promise.all([fireAllKnownShots(pageA, knownShipCells), fireAllKnownShots(pageB, knownShipCells)]);
    });

    await test.step('Both players naturally auto-navigate Gameplay -> Results', async () => {
      // Real, poll-driven transition (GameplayScreen's hasWinner effect) - the
      // highest-risk transition per the original bug report. No force-navigation.
      await pageA.waitForURL(/\/game\/results$/, { timeout: 60000 });
      await pageB.waitForURL(/\/game\/results$/, { timeout: 60000 });
    });

    await test.step('Results screen: one win, one loss, correct stats, read-only boards', async () => {
      const heroTitleA = pageA.locator('.result-hero .title');
      const heroTitleB = pageB.locator('.result-hero .title');
      await expect(heroTitleA).toBeVisible();
      await expect(heroTitleB).toBeVisible();

      const [textA, textB] = await Promise.all([heroTitleA.textContent(), heroTitleB.textContent()]);
      expect([textA, textB].sort()).toEqual(['Defeat', 'Victory!']);

      for (const page of [pageA, pageB]) {
        await expect(page.getByText('Ships sunk')).toBeVisible();
        await expect(page.locator('.stat-strip .b')).toHaveCount(1);
        await expect(page.getByText('Hits', { exact: true })).toHaveCount(0);
        await expect(page.getByText('Time', { exact: true })).toHaveCount(0);

        // Both boards present and read-only.
        await expect(page.locator('.board-panel .board.readonly')).toHaveCount(2);
      }

      // Bug 2 (opponent's un-hit ships not shown on the Results screen): this spec's
      // shot-targeting logic only ever hits known ship cells directly and never revisits
      // a cell, so the loser here never gets an active turn (the winner keeps hitting
      // until the game ends) - meaning the winner's board stays 100% un-hit from the
      // loser's perspective by the time the game ends. That's exactly the bug's trigger
      // scenario, so confirm the loser's result-target board (first `.board-panel`,
      // opponent's board per ResultsScreen.tsx's markup) still shows the opponent's
      // ship cells.
      const defeatPage = textA === 'Defeat' ? pageA : pageB;
      const opponentBoardShipCells = defeatPage.locator('.board-panel').first().locator('.cell-ship');
      await expect(opponentBoardShipCells).not.toHaveCount(0);
    });
  } finally {
    await contextA.close();
    await contextB.close();
  }
});
