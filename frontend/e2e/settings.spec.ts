import { expect, test } from './fixtures/stack';
import type { Browser, Page } from '@playwright/test';
import type { components } from '../src/contract/generated/api';

type Snapshot = components['schemas']['GameSnapshot'];

async function prepareFleet(page: Page) {
    const placed = page.waitForResponse((response) => response.url().endsWith('/commands'));
    await page.getByRole('button', { name: 'Random', exact: true }).click();
    expect((await placed).ok()).toBeTruthy();
    await page.getByRole('button', { name: 'Ready', exact: true }).click();
    const ready = page.waitForResponse((response) => response.url().endsWith('/commands'));
    await page.getByRole('button', { name: 'Commit and wait', exact: true }).click();
    expect((await ready).ok()).toBeTruthy();
}

async function startSeaBattle(page: Page, browser: Browser, origin: string) {
    await page.goto(`${origin}/create`);
    await page.getByRole('radio').filter({ hasText: 'Sea Battle' }).click();
    await page.getByLabel('Captain name').fill('Host');
    const created = page.waitForResponse(
        (response) => response.request().method() === 'POST' && response.url().endsWith('/api/v1/games'),
    );
    await page.getByRole('button', { name: 'Create game', exact: true }).click();
    const initial = (await (await created).json()) as Snapshot;
    if (!initial.invitationUrl) throw new Error('Invitation missing');
    const guest = await browser.newPage();
    await guest.goto(`${origin}/join/${initial.gameId}${new URL(initial.invitationUrl).hash}`);
    await guest.getByLabel('Captain name').fill('Guest');
    const joined = guest.waitForResponse((response) => response.url().endsWith('/join'));
    await guest.getByRole('button', { name: 'Join game', exact: true }).click();
    await joined;
    await expect(page.getByRole('grid', { name: 'Your sea' })).toBeVisible();
    await prepareFleet(page);
    await prepareFleet(guest);
    await expect(page.getByRole('grid', { name: 'Enemy waters' })).toBeVisible();
    const snapshot = (await (await page.request.get(`/api/v1/games/${initial.gameId}`)).json()) as Snapshot;
    return { guest, gameId: initial.gameId, shooter: snapshot.turn === 'YOU' ? page : guest };
}

test('settings apply and persist language and theme, and follow system theme changes', async ({ page, stack }) => {
    await page.goto(stack.origin);
    await page.getByRole('button', { name: 'Settings' }).click();
    const dialog = page.getByRole('dialog', { name: 'Settings' });
    await expect(dialog).toBeVisible();
    await page.getByRole('button', { name: 'Light' }).click();
    await page.getByLabel('Language').selectOption('uk');
    await expect(page.getByRole('heading', { name: 'Налаштування' })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
    await page.getByRole('button', { name: 'Готово' }).click();
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Морський командний центр' })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');

    await page.getByRole('button', { name: 'Налаштування' }).click();
    await page.getByRole('button', { name: 'Системна' }).click();
    await page.getByRole('button', { name: 'Готово' }).click();
    await page.emulateMedia({ colorScheme: 'light' });
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
    await page.emulateMedia({ colorScheme: 'dark' });
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    await expect
        .poll(() => page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--accent').trim()))
        .toBe('#3becb0');

    await page.getByRole('button', { name: 'Налаштування' }).click();
    await page.getByRole('button', { name: 'Світла' }).click();
    await page.getByRole('button', { name: 'Готово' }).click();
    await expect
        .poll(() => page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--accent').trim()))
        .toBe('#1d7fa8');

    const opener = page.getByRole('button', { name: 'Налаштування' });
    await opener.click();
    const settingsDialog = page.getByRole('dialog', { name: 'Налаштування' });
    await expect(settingsDialog).toBeVisible();
    await expect(settingsDialog.locator(':focus')).toBeVisible();
    await page.keyboard.press('Shift+Tab');
    expect(await settingsDialog.evaluate((element) => element.contains(document.activeElement))).toBe(true);
    await expect(settingsDialog.getByRole('button', { name: 'Готово' })).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(settingsDialog.getByRole('button', { name: 'Закрити' })).toBeFocused();
    await page.keyboard.press('Escape');
    await expect(settingsDialog).not.toBeVisible();
    await expect(opener).toBeFocused();

    const newGame = page.getByRole('button', { name: 'Нова гра' });
    const control = await newGame.evaluate((element) => ({
        height: getComputedStyle(element).minHeight,
        touchAction: getComputedStyle(element).touchAction,
    }));
    expect(Number.parseFloat(control.height)).toBeGreaterThanOrEqual(44);
    expect(control.touchAction).toBe('manipulation');
});

test('sound and haptics toggled during play leave the server shot result and turn unchanged', async ({
    page,
    browser,
    stack,
}) => {
    const { guest, gameId, shooter } = await startSeaBattle(page, browser, stack.origin);
    let active = shooter;
    const shots = [
        { enabled: false, coordinate: 'J10', target: { rowIndex: 9, columnIndex: 9 } },
        { enabled: true, coordinate: 'A1', target: { rowIndex: 0, columnIndex: 0 } },
    ] as const;
    try {
        for (const { enabled, coordinate, target } of shots) {
            await active.getByRole('button', { name: 'Settings' }).click();
            const dialog = active.getByRole('dialog', { name: 'Settings' });
            const sound = dialog.getByRole('checkbox', { name: /Sound feedback/ });
            const haptics = dialog.getByRole('checkbox', { name: /Haptic feedback/ });
            if ((await sound.isChecked()) === enabled) await sound.setChecked(!enabled);
            if ((await haptics.isChecked()) === enabled) await haptics.setChecked(!enabled);
            await expect(sound).toBeChecked({ checked: !enabled });
            await expect(haptics).toBeChecked({ checked: !enabled });
            await sound.setChecked(enabled);
            await haptics.setChecked(enabled);
            await expect(sound).toBeChecked({ checked: enabled });
            await expect(haptics).toBeChecked({ checked: enabled });
            await dialog.getByRole('button', { name: 'Done' }).click();

            const command = active.waitForResponse((response) => response.url().endsWith('/commands'));
            await active
                .getByRole('grid', { name: 'Enemy waters' })
                .getByRole('gridcell', { name: `${coordinate}, unknown`, exact: true })
                .click();
            expect((await command).ok()).toBeTruthy();
            const server = (await (await active.request.get(`/api/v1/games/${gameId}`)).json()) as Snapshot;
            const shot = server.lastShot;
            expect(shot).toBeDefined();
            if (!shot) throw new Error('Server snapshot did not include the accepted shot');
            expect(shot.target).toEqual(target);
            const cellState = { MISS: 'miss', HIT: 'hit', SUNK: 'sunk' }[shot.result];
            await expect(
                active.getByText(server.turn === 'YOU' ? 'Your turn · Tap an enemy cell' : 'Opponent’s turn'),
            ).toBeVisible();
            await expect(
                active
                    .getByRole('grid', { name: 'Enemy waters' })
                    .getByRole('gridcell', { name: `${coordinate}, ${cellState}`, exact: true }),
            ).toBeVisible();
            active = server.turn === 'YOU' ? active : active === page ? guest : page;
        }
    } finally {
        await guest.close();
    }
});
