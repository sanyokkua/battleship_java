import {defineConfig, devices} from "@playwright/test";

// https://playwright.dev/docs/test-configuration
export default defineConfig({
    testDir: "./e2e",
    fullyParallel: true,
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 2 : 0,
    reporter: "html",
    use: {
        baseURL: "http://localhost:5173",
        trace: "on-first-retry",
    },
    projects: [
        {
            name: "chromium",
            use: {...devices["Desktop Chrome"]},
        },
        {
            name: "firefox",
            use: {...devices["Desktop Firefox"]},
        },
        {
            name: "webkit",
            use: {...devices["Desktop Safari"]},
        },
        // Real touch-event emulation (hasTouch/isMobile), unlike the desktop projects
        // above or the ad hoc page.setViewportSize() used elsewhere for narrow-viewport
        // checks — scoped to the tap-cell popup spec only, not the whole suite, since
        // running every existing spec against a fourth device/browser combination would
        // meaningfully slow the suite for coverage this feature doesn't need elsewhere.
        {
            name: "mobile-chrome",
            use: {...devices["Pixel 5"]},
            testMatch: /ship-placement-popup\.spec\.ts/,
        },
    ],
    webServer: {
        command: "npm run dev:mock",
        url: "http://localhost:5173",
        reuseExistingServer: !process.env.CI,
    },
});
