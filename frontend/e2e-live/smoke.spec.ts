import { test, expect } from '@playwright/test';

// Smoke test for the live e2e harness (playwright.live.config.ts): proves the
// webServer plumbing works end-to-end against the real packaged Spring Boot
// JAR (built via `mvn clean package` from the repo root) before any full-game
// specs are layered on top. Deliberately minimal.
test('home screen loads and shows New Game', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('button', { name: 'New Game' })).toBeVisible();
});
