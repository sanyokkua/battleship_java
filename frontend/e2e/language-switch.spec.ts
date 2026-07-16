import { test, expect } from '@playwright/test';

const EN_TAGLINE = 'Sink the enemy fleet before they sink yours.';
const UK_TAGLINE = 'Потопіть ворожий флот раніше, ніж він потопить ваш.';

test.describe('language switch (EN <-> УКР)', () => {
  test('switching to УКР updates on-screen text instantly, without a page reload, and persists across a reload', async ({
    page,
  }) => {
    await page.goto('/');

    // Baseline English text: hero tagline + nav labels.
    await expect(page.getByText(EN_TAGLINE)).toBeVisible();
    await expect(page.getByRole('link', { name: 'Home' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'New' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Join' })).toBeVisible();

    // A marker on `window` only survives as long as the JS context does - a full page
    // navigation/reload would wipe it. Used below to prove the language switch is a
    // pure in-app re-render, not a navigation.
    await page.evaluate(() => {
      (window as unknown as { __e2eNoReloadMarker?: string }).__e2eNoReloadMarker = 'still-here';
    });

    await page.getByRole('button', { name: 'УКР' }).click();

    await expect(page.getByText(UK_TAGLINE)).toBeVisible();
    await expect(page.getByText(EN_TAGLINE)).toHaveCount(0);

    const markerSurvived = await page.evaluate(
      () => (window as unknown as { __e2eNoReloadMarker?: string }).__e2eNoReloadMarker,
    );
    expect(markerSurvived).toBe('still-here');

    // The УКР control itself reflects the active language.
    await expect(page.getByRole('button', { name: 'УКР' })).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByRole('button', { name: 'EN' })).toHaveAttribute('aria-pressed', 'false');

    // Selection persists (localStorage via i18next-browser-languagedetector) across a real reload.
    await page.reload();
    await expect(page.getByText(UK_TAGLINE)).toBeVisible();
    await expect(page.getByRole('button', { name: 'УКР' })).toHaveAttribute('aria-pressed', 'true');
  });

  test('a fresh browser context defaults to English', async ({ page }) => {
    // Playwright gives every test its own isolated BrowserContext (fresh localStorage) by
    // default, so simply loading the app here - with nothing carried over from the test
    // above - is itself the "fresh/cleared context" case.
    await page.goto('/');

    await expect(page.getByText(EN_TAGLINE)).toBeVisible();
    await expect(page.getByRole('button', { name: 'EN' })).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByRole('button', { name: 'УКР' })).toHaveAttribute('aria-pressed', 'false');
  });
});
