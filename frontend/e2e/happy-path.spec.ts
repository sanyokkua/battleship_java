import {expect, test} from '@playwright/test';
import {
    computeFleetLayout,
    coordinateLabel,
    coordinateLabelPattern,
    createOpponent,
    fetchShipCatalog,
    fetchStage,
    hardNavigate,
    persistStage,
    placeFullFleetAndReady,
    placeFullFleetViaUi,
    readPersistedSession,
    shootViaBackdoor,
} from './support/mockBackdoor';

// No clipboard permissions are granted here on purpose: the Wait screen's "Copy"
// control uses the `copy-to-clipboard` library, which falls back to a hidden-element
// + document.execCommand('copy') whenever the async navigator.clipboard API isn't
// available or isn't permitted, and never throws (see WaitScreen.tsx's handleCopy).
// Granting 'clipboard-read'/'clipboard-write' via test.use({ permissions }) is a
// Chromium-only capability - Firefox and WebKit reject those permission strings
// outright ("Unknown permission") and fail before the page even loads - so this test
// must not request them if it's going to run cross-browser.

test('happy path: create -> wait -> prepare -> play -> results (win)', async ({page}) => {
    // Three stage-transition boundaries plus ~30 real-UI board interactions comfortably
    // exceed Playwright's default 30s test timeout.
    test.setTimeout(90000);

    await page.goto('/');

    // Home -> New Game.
    await page.getByRole('button', {name: 'New Game'}).click();
    await expect(page).toHaveURL(/\/new$/);

    // New Game: pick an edition mode card, enter a name, submit.
    await expect(page.getByRole('radio', {name: /Milton Bradley/i})).toBeVisible();
    await page.getByRole('radio', {name: /Milton Bradley/i}).click();
    await page.getByLabel('Player name').fill('Ada');
    await page.getByRole('button', {name: 'Start new game'}).click();

    // Wait screen: greeting, waiting state, copy control -> "copied" toast. This is the
    // first (and, in this flow, only "natural") route transition, so it works fine as-is
    // - see mockBackdoor.ts's module doc comment for why later transitions don't.
    await expect(page).toHaveURL(/\/game\/wait$/);
    await expect(page.getByText('Hello, Ada!')).toBeVisible();
    await expect(page.getByText('Waiting for your opponent')).toBeVisible();

    await page.getByRole('button', {name: 'Copy', exact: true}).click();
    await expect(page.getByText('Game ID copied to clipboard.')).toBeVisible();

    const {sessionId, player} = await readPersistedSession(page);
    expect(sessionId).toBeTruthy();
    expect(player).not.toBeNull();

    // Backdoor: bring in an opponent and place + ready their whole fleet (bypassing the
    // UI entirely - this is the "second player" every spec needs, driven through the
    // same page's mock adapter instance since there is no cross-tab state sharing).
    const opponent = await createOpponent(page, sessionId, 'Bandit');
    const opponentPlacements = await placeFullFleetAndReady(page, sessionId, opponent.playerId);

    // Move on to Preparation. (The app's own poll-driven Wait -> Preparation
    // auto-navigation is currently broken by a pre-existing bug - see mockBackdoor.ts's
    // module doc comment for the full write-up. hardNavigate is this ticket's in-scope
    // workaround; the underlying bug is flagged separately for its own fix.)
    await persistStage(page, 'PREPARATION');
    await hardNavigate(page, '/game/preparation');
    await expect(page.getByText('Place your fleet')).toBeVisible();

    // Place Player 1's whole fleet via real UI interaction, exercising the guided
    // placement popup's direction step for one ship (the smallest ship in the fleet is
    // placed VERTICAL - see computeFleetLayout's `verticalFirst` doc comment for why
    // that one is always safe).
    const ownShips = await fetchShipCatalog(page, sessionId, player!.playerId);
    const ownPlacements = computeFleetLayout(ownShips, {verticalFirst: true});
    expect(ownPlacements.some((p) => p.direction === 'VERTICAL')).toBe(true);

    await placeFullFleetViaUi(page, ownPlacements, {horizontal: /Horizontal/i, vertical: /Vertical/i});

    const readyButton = page.getByRole('button', {name: /Ready to go!/});
    await expect(readyButton).toBeEnabled();
    await readyButton.click();

    // The opponent was already readied via the backdoor step, so both players are now
    // ready and the server flips straight to IN_GAME.
    await expect.poll(() => fetchStage(page, sessionId)).toBe('IN_GAME');

    await persistStage(page, 'IN_GAME');
    await hardNavigate(page, '/game/gameplay');
    await expect(page.locator('.scoreboard')).toBeVisible();

    // Shoot at every opponent ship cell via real UI clicks, using the coordinates the
    // backdoor placement returned. The mock adapter only passes the turn on a MISS, so
    // an all-hits sequence like this keeps Player 1 active the whole way through.
    //
    // The very last cell (the shot that actually sinks the last ship and wins the
    // game) is fired through the backdoor instead of a real click - see
    // shootViaBackdoor's doc comment: firing it for real would make GameplayScreen's
    // own client-side state observe `hasWinner` immediately, which can trigger its
    // broken auto-navigate-to-results (the same pre-existing bug `hardNavigate` works
    // around elsewhere in this file) before this test's own code gets a turn to call
    // hardNavigate itself. Every other shot - i.e. the entire win condition being
    // exercised - is still fired via real UI clicks.
    const allCells = opponentPlacements.flatMap((placement) => placement.cells);
    const uiCells = allCells.slice(0, -1);
    const finalCell = allCells[allCells.length - 1];

    const targetBoard = page.locator('.bp-target .board');
    for (const cell of uiCells) {
        await targetBoard.getByRole('button', {name: coordinateLabelPattern(cell)}).click();
        // Wait for this shot to resolve (cell state moves off "water") before firing
        // the next one, per the no-fixed-sleeps rule - the mock adapter/refetch is
        // async, and rapid-fire clicks without this could race ahead of state updates.
        await expect(
            targetBoard.getByRole('button', {name: `${coordinateLabel(cell)}, water`}),
        ).toHaveCount(0);
    }

    const finalShotResult = await shootViaBackdoor(page, sessionId, player!.playerId, finalCell);
    expect(finalShotResult).toBe('DESTROYED');

    // Victory. (The app's own poll-driven Gameplay -> Results auto-navigation hits the
    // same pre-existing bug as Wait -> Preparation above - hardNavigate again.)
    await expect.poll(() => fetchStage(page, sessionId)).toBe('FINISHED');
    await persistStage(page, 'FINISHED');
    await hardNavigate(page, '/game/results');

    // Results screen shows only the "Ships sunk" stat (Hits/Time are
    // explicitly excluded, not API-backed).
    await expect(page.getByText('Victory!')).toBeVisible();
    await expect(page.getByText('Ships sunk')).toBeVisible();
    await expect(page.locator('.stat-strip .b')).toHaveCount(1);
    await expect(page.getByText('Hits', {exact: true})).toHaveCount(0);
    await expect(page.getByText('Time', {exact: true})).toHaveCount(0);
});
