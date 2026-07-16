import {expect, type Page, test} from '@playwright/test';
import {
    coordinateLabel,
    coordinateLabelPattern,
    createOpponent,
    hardNavigate,
    persistStage,
    readPersistedSession,
} from './support/mockBackdoor';

/**
 * Covers the tap-cell popups: tapping an empty cell opens a guided ship+direction
 * picker, and tapping any cell of an already-placed ship opens a rotate/remove popup.
 * Both popups render as a `role="dialog"` (a centered modal on desktop, a bottom sheet
 * on mobile via CSS only — the underlying markup/roles are identical either way).
 */
async function seedToPreparation(page: Page, name: string) {
    await page.goto('/');
    await page.getByRole('button', {name: 'New Game'}).click();
    await page.getByRole('radio', {name: /Ukrainian/i}).click();
    await page.getByLabel('Player name').fill(name);
    await page.getByRole('button', {name: 'Start new game'}).click();
    await expect(page).toHaveURL(/\/game\/wait$/);

    const {sessionId, player} = await readPersistedSession(page);
    await createOpponent(page, sessionId, 'Bystander');
    await persistStage(page, 'PREPARATION');
    await hardNavigate(page, '/game/preparation');
    await expect(page.getByText('Place your fleet')).toBeVisible();

    return {sessionId, player: player!};
}

/**
 * Places a ship via the tap-empty-cell guided popup: taps `at`, picks the ship-picking
 * step's option matching `shipSize`, then (for ships larger than one cell) picks
 * `direction` from the direction step.
 */
async function placeShipViaPopup(
    page: Page,
    board: ReturnType<Page['locator']>,
    at: { row: number; column: number },
    shipSize: number,
    direction: 'HORIZONTAL' | 'VERTICAL' = 'HORIZONTAL',
) {
    await board.getByRole('button', {name: coordinateLabelPattern(at)}).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.locator('.ship-placement-option', {hasText: `${shipSize} cell`}).click();
    if (shipSize > 1) {
        const directionButton =
            direction === 'VERTICAL'
                ? dialog.getByRole('button', {name: 'Vertical'})
                : dialog.getByRole('button', {name: 'Horizontal'});
        await directionButton.click();
    }
    await expect(dialog).toBeHidden();
}

test.describe('tap-cell ship placement and rotate/remove popups', () => {
    test('tapping an empty cell opens the guided popup; picking a ship and a direction places it', async ({page}) => {
        test.setTimeout(45000);
        await seedToPreparation(page, 'Guided');

        const board = page.locator('.board-panel .board');
        const anchor = {row: 0, column: 0};
        await board.getByRole('button', {name: coordinateLabelPattern(anchor)}).click();

        const dialog = page.getByRole('dialog');
        await expect(dialog).toBeVisible();
        await expect(dialog.getByText('Choose a ship')).toBeVisible();

        await dialog.getByRole('button').first().click();
        await expect(dialog.getByText('Choose a direction')).toBeVisible();
        await dialog.getByRole('button', {name: 'Horizontal'}).click();

        await expect(dialog).toBeHidden();
        await expect(page.getByText('1 of 10 ships placed')).toBeVisible();
        await expect(board.getByRole('button', {name: coordinateLabelPattern(anchor)})).toHaveAccessibleName(
            `${coordinateLabel(anchor)}, your ship`,
        );
    });

    test('tapping a placed ship opens the action popup; Remove removes it', async ({page}) => {
        test.setTimeout(45000);
        await seedToPreparation(page, 'Remover');

        const board = page.locator('.board-panel .board');
        const anchor = {row: 0, column: 0};

        await placeShipViaPopup(page, board, anchor, 1);
        await expect(page.getByText('1 of 10 ships placed')).toBeVisible();

        await board.getByRole('button', {name: coordinateLabelPattern(anchor)}).click();
        const dialog = page.getByRole('dialog');
        await expect(dialog).toBeVisible();
        await dialog.getByRole('button', {name: 'Remove'}).click();

        await expect(dialog).toBeHidden();
        await expect(page.getByText('0 of 10 ships placed')).toBeVisible();
        await expect(board.getByRole('button', {name: coordinateLabelPattern(anchor)})).toHaveAccessibleName(
            `${coordinateLabel(anchor)}, water`,
        );
    });

    test('rotating a placed ship with room to rotate moves it to the other orientation', async ({page}) => {
        test.setTimeout(45000);
        await seedToPreparation(page, 'Rotator');

        // Place the Battleship (size 4, the only one in the Ukrainian edition) HORIZONTAL
        // at (4,4), far from every board edge in both orientations.
        const board = page.locator('.board-panel .board');
        const bow = {row: 4, column: 4};
        await placeShipViaPopup(page, board, bow, 4, 'HORIZONTAL');
        await expect(page.getByText('1 of 10 ships placed')).toBeVisible();

        await board.getByRole('button', {name: coordinateLabelPattern(bow)}).click();
        const dialog = page.getByRole('dialog');
        await expect(dialog).toBeVisible();
        await expect(dialog.getByRole('button', {name: 'Rotate'})).toBeVisible();
        await dialog.getByRole('button', {name: 'Rotate'}).click();

        await expect(dialog).toBeHidden();

        // Now VERTICAL from the same bow: (4,4)-(7,4) are the ship.
        for (let i = 0; i < 4; i++) {
            await expect(
                board.getByRole('button', {name: coordinateLabelPattern({row: bow.row + i, column: bow.column})}),
            ).toHaveAccessibleName(`${coordinateLabel({row: bow.row + i, column: bow.column})}, your ship`);
        }
        // Its old HORIZONTAL footprint (4,5)-(4,7) is no longer "your ship" — each cell is
        // either open water (a button) or back to plain moat (a non-clickable div, if it's
        // still adjacent to the new vertical footprint), so match on the aria-label
        // attribute directly rather than assuming a specific role/state.
        for (let i = 1; i < 4; i++) {
            const oldCell = {row: bow.row, column: bow.column + i};
            const cellEl = board.locator(`[aria-label^="${coordinateLabel(oldCell)}, "]`);
            await expect(cellEl).toHaveAttribute('aria-label', new RegExp(`^${coordinateLabel(oldCell)}, (water|blocked)$`));
        }
    });

    test('hides Rotate when the ship has no room to rotate in place', async ({page}) => {
        test.setTimeout(45000);
        await seedToPreparation(page, 'NoRotate');

        // Flush against the bottom edge: rotating VERTICAL from here would go off the
        // board, so only HORIZONTAL is offered in the popup's direction step.
        const board = page.locator('.board-panel .board');
        const bow = {row: 9, column: 0};
        await placeShipViaPopup(page, board, bow, 4, 'HORIZONTAL');
        await expect(page.getByText('1 of 10 ships placed')).toBeVisible();

        await board.getByRole('button', {name: coordinateLabelPattern(bow)}).click();
        const dialog = page.getByRole('dialog');
        await expect(dialog).toBeVisible();
        await expect(dialog.getByRole('button', {name: 'Rotate'})).toHaveCount(0);
        await expect(dialog.getByRole('button', {name: 'Remove'})).toBeVisible();
    });
});
