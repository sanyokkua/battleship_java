import { expect, test } from './fixtures/stack';

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
