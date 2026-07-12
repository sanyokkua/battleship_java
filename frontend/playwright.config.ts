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
    ],
    webServer: {
        command: "npm run dev:mock",
        url: "http://localhost:5173",
        reuseExistingServer: !process.env.CI,
    },
});
