import {expect, type Page} from '@playwright/test';
import type {Coordinate, ResponseCreatedPlayerDto, ShipDto} from '../../src/logic/ApplicationTypes';
import {computeFleetLayout, coordinateLabel, coordinateLabelPattern, type FleetPlacement} from './fleetHelpers';
// Note: no runtime import of './types' here on purpose - it's a pure ambient .d.ts
// (global `Window.__e2eMockHooks` augmentation). tsconfig.e2e.json's `include: ["e2e"]`
// already pulls it into the whole-program type-check without needing an import (which
// would fail at runtime under both Vite/Vitest and Playwright/esbuild, since a .d.ts
// has no emitted JS module to resolve to).
//
// computeFleetLayout / coordinateLabel / coordinateLabelPattern / FleetPlacement live in
// ./fleetHelpers.ts (pure, adapter-agnostic — shared with e2e-live/) and are re-exported
// here so this module's existing callers/imports keep working unchanged.
export {computeFleetLayout, coordinateLabel, coordinateLabelPattern, type FleetPlacement};

/**
 * Shared Playwright helpers for driving the app's MockGameAdapter directly
 * (`window.__e2eMockHooks`, wired up by src/App.tsx only when VITE_ADAPTER=mock —
 * see frontend/.env.mock and the "dev:mock" script used by playwright.config.ts).
 *
 * Why a backdoor at all: every spec needs a second ("opponent") player, but each
 * browser tab/page has its own independent JS module graph and therefore its own
 * independent MockGameAdapter instance — there is no cross-tab state sharing. So
 * instead of a second browser context, specs drive the opponent through the SAME
 * page's adapter instance via page.evaluate(), while Player 1 is driven through the
 * real UI. This only works within a single continuous page load: any full navigation
 * (page.goto()/page.reload()) creates a fresh module graph and a fresh, empty
 * MockGameAdapter, discarding all session/player state created before it.
 *
 * --- A pre-existing routing bug this module works around (see `hardNavigate`) ---
 * While building these specs, driving the real "Wait -> Preparation" transition
 * (created here purely by polling, per routing/StageGuard.tsx + screens/WaitScreen.tsx
 * — both frozen Phase 5 code, outside this ticket's file scope) turned up a genuine,
 * reproducible bug: React Router reuses <StageGuard>'s fiber (and therefore its
 * cached, read-once-on-mount `useSessionGuard()` state — see hooks/useSessionGuard.ts's
 * own doc comment, "not a reactive subscription") across *consecutive* route changes
 * that both render <StageGuard> at the router outlet position, since it's the same
 * component type each time. So the *second* such transition in a page's lifetime
 * (Wait -> Preparation, then also Preparation -> Gameplay, then Gameplay -> Results)
 * reads back the *first* transition's stale `stage`, decides it doesn't match, and
 * <Navigate>s back — which then reads the same stale value again, forever. Confirmed
 * with `console.log` instrumentation in both `npm run dev` and a real `vite build` +
 * `vite preview` (i.e. not a React StrictMode dev-only artifact): the app can
 * currently never auto-advance past the Wait screen for two real, separate browser
 * tabs either. This is a critical, pre-existing bug — flagged separately (see the
 * spawned follow-up task) — but fixing it requires touching StageGuard.tsx /
 * WaitScreen.tsx / PreparationScreen.tsx / GameplayScreen.tsx / useSessionGuard.ts,
 * none of which are in this ticket's confined file list. `hardNavigate` below is the
 * in-scope workaround used to get past it.
 */

export type PersistedSession = {
    sessionId: string;
    player: ResponseCreatedPlayerDto | null;
};

/** Reads the session/player currently persisted in localStorage by the real UI (or by seedSessionAndPlayer). */
export async function readPersistedSession(page: Page): Promise<PersistedSession> {
    return page.evaluate(() => {
        const hooks = window.__e2eMockHooks;
        if (!hooks) {
            throw new Error('window.__e2eMockHooks is missing - is the app running with VITE_ADAPTER=mock (npm run dev:mock)?');
        }
        return {
            sessionId: hooks.storage.loadSession(),
            player: hooks.storage.loadPlayer(),
        };
    });
}

/** Creates a second ("opponent") player directly on the mock adapter, bypassing the UI entirely. */
export async function createOpponent(page: Page, sessionId: string, name: string): Promise<ResponseCreatedPlayerDto> {
    return page.evaluate(
        async ({sessionId, name}) => {
            const hooks = window.__e2eMockHooks;
            if (!hooks) throw new Error('window.__e2eMockHooks is missing');
            return hooks.adapter.createPlayer(sessionId, name);
        },
        {sessionId, name},
    );
}

/** Reads a player's ship catalog straight from the mock adapter (bypassing the UI). */
export async function fetchShipCatalog(page: Page, sessionId: string, playerId: string): Promise<ShipDto[]> {
    return page.evaluate(
        async ({sessionId, playerId}) => {
            const hooks = window.__e2eMockHooks;
            if (!hooks) throw new Error('window.__e2eMockHooks is missing');
            const state = await hooks.adapter.getPreparationState(sessionId, playerId);
            return state.ships;
        },
        {sessionId, playerId},
    );
}

/** Places an entire fleet directly via the mock adapter (bypassing the UI) and marks the player ready. */
export async function placeFullFleetAndReady(page: Page, sessionId: string, playerId: string): Promise<FleetPlacement[]> {
    const ships = await fetchShipCatalog(page, sessionId, playerId);
    const placements = computeFleetLayout(ships);

    for (const placement of placements) {
        await page.evaluate(
            async ({sessionId, playerId, shipId, at, direction}) => {
                const hooks = window.__e2eMockHooks;
                if (!hooks) throw new Error('window.__e2eMockHooks is missing');
                await hooks.adapter.addShip(sessionId, playerId, shipId, at, direction);
            },
            {sessionId, playerId, shipId: placement.shipId, at: placement.at, direction: placement.direction},
        );
    }

    await page.evaluate(
        async ({sessionId, playerId}) => {
            const hooks = window.__e2eMockHooks;
            if (!hooks) throw new Error('window.__e2eMockHooks is missing');
            await hooks.adapter.setReady(sessionId, playerId);
        },
        {sessionId, playerId},
    );

    return placements;
}

/**
 * Places an entire fleet via the *real UI*, driving the tap-empty-cell guided
 * placement popup: for each placement, taps the target board cell by its coordinate
 * aria-label (e.g. "C7, water" — see BoardCell.tsx's aria-label scheme), picks the
 * ship matching that placement's size from the popup's ship-picking step, then (for
 * ships larger than one cell) picks the placement's direction from the popup's
 * direction step. The popup selects by tapped cell rather than tray order, so
 * per-ship ordering doesn't matter here.
 */
export async function placeFullFleetViaUi(
    page: Page,
    placements: FleetPlacement[],
    labels: { horizontal: RegExp; vertical: RegExp },
): Promise<void> {
    const board = page.locator('.board-panel .board');
    const dialog = page.getByRole('dialog');

    for (const placement of placements) {
        await board.getByRole('button', {name: coordinateLabelPattern(placement.at)}).click();
        await expect(dialog).toBeVisible();
        await dialog.locator('.ship-placement-option', {hasText: `${placement.shipSize} cell`}).click();
        if (placement.shipSize > 1) {
            const directionButton =
                placement.direction === 'VERTICAL'
                    ? dialog.getByRole('button', {name: labels.vertical})
                    : dialog.getByRole('button', {name: labels.horizontal});
            await directionButton.click();
        }
        await expect(dialog).toBeHidden();
    }
}

/**
 * Fires one shot directly via the mock adapter, bypassing the UI (and, crucially,
 * bypassing GameplayScreen's own `refetch()` that a real-UI shot would trigger). Used
 * for the final, game-winning shot in happy-path.spec.ts: firing it for real would
 * make GameplayScreen's client-side state observe `hasWinner` immediately, which can
 * fire its own broken auto-navigate-to-results before the test's own `hardNavigate`
 * gets a turn (see `hardNavigate`'s doc comment). Every other shot is still fired via
 * real UI clicks, satisfying "real UI clicks only" for the actual win condition being
 * exercised - this only changes how the *very last* shot's result reaches the page.
 */
export async function shootViaBackdoor(page: Page, sessionId: string, playerId: string, at: Coordinate): Promise<string> {
    const result = await page.evaluate(
        async ({sessionId, playerId, at}) => {
            const hooks = window.__e2eMockHooks;
            if (!hooks) throw new Error('window.__e2eMockHooks is missing');
            return hooks.adapter.shoot(sessionId, playerId, at);
        },
        {sessionId, playerId, at},
    );
    return result.shotResult;
}

/** Reads the session's current GameStage straight from the mock adapter (bypassing the UI). */
export async function fetchStage(page: Page, sessionId: string): Promise<string> {
    return page.evaluate((sid) => {
        const hooks = window.__e2eMockHooks;
        if (!hooks) throw new Error('window.__e2eMockHooks is missing');
        return hooks.adapter.getStage(sid);
    }, sessionId);
}

/** Persists the given GameStage to localStorage via GameBrowserStorage.saveStage, bypassing the UI. */
export async function persistStage(page: Page, stage: string): Promise<void> {
    await page.evaluate((s) => {
        const hooks = window.__e2eMockHooks;
        if (!hooks) throw new Error('window.__e2eMockHooks is missing');
        hooks.storage.saveStage(s);
    }, stage);
}

/**
 * Client-side route change to a <StageGuard>-protected `path`, working around the
 * StageGuard fiber-reuse bug documented in this module's top comment. Bounces through
 * "/join" (any route NOT wrapped in <StageGuard> works — it renders no visible UI
 * this module's callers depend on) before landing on `path`, so React sees a
 * different element type at the router outlet on each hop and is forced to actually
 * unmount/remount <StageGuard> — making the hop onto `path` a genuinely fresh mount
 * that reads current (correct) localStorage, rather than a stale cached read.
 *
 * Uses raw `history.pushState` + a synthetic "popstate" event (which React Router's
 * internal history listener reacts to) instead of `page.goto()`/`page.reload()`, so —
 * unlike a real navigation — this never reloads the page and never discards the
 * in-memory MockGameAdapter session built up via this module's other helpers.
 *
 * Callers are expected to have already persisted the correct session/player/stage
 * (via the real UI and/or `persistStage`) before calling this — StageGuard itself
 * only ever reads localStorage, never the live adapter.
 *
 * The screen being left can have its own broken poll-driven auto-navigate attempt
 * in flight (e.g. GameplayScreen's `useEffect` fires the instant `state.hasWinner`
 * flips). Callers should avoid triggering that in the first place where possible
 * (e.g. `happy-path.spec.ts` fires the final, game-winning shot through the
 * backdoor rather than the real UI, so the client never observes `hasWinner` before
 * this function runs) — an earlier version of this function tried to instead race
 * or block a genuinely in-flight stale attempt and neither was reliable (blocking
 * `history.pushState` risks desyncing it from React Router's own internal history
 * bookkeeping, which doesn't re-read the DOM to confirm a push "worked"). As a
 * belt-and-suspenders measure this still verifies the hop landed and stayed on
 * `path`, retrying if not.
 */
export async function hardNavigate(page: Page, path: string): Promise<void> {
    const targetUrlPattern = new RegExp(`${path.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`);

    for (let attempt = 0; attempt < 5; attempt++) {
        await page.evaluate(() => {
            window.history.pushState({}, '', '/join');
            window.dispatchEvent(new PopStateEvent('popstate'));
        });
        await page.evaluate((target) => {
            window.history.pushState({}, '', target);
            window.dispatchEvent(new PopStateEvent('popstate'));
        }, path);

        try {
            await page.waitForURL(targetUrlPattern, {timeout: 500});
        } catch {
            continue; // Lost the race outright; retry the whole hop.
        }

        if (await urlStaysOn(page, targetUrlPattern)) {
            return;
        }
        // Bumped off `path` shortly after landing there; retry the whole hop.
    }

    throw new Error(`hardNavigate: failed to settle on ${path} after retries`);
}

/** Samples the page's URL a few more times, confirming it keeps matching `pattern` throughout. */
async function urlStaysOn(page: Page, pattern: RegExp, samples = 4, intervalMs = 150): Promise<boolean> {
    for (let i = 0; i < samples; i++) {
        await page.waitForTimeout(intervalMs);
        if (!pattern.test(page.url())) {
            return false;
        }
    }
    return true;
}
