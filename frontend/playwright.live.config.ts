import {defineConfig, devices} from "@playwright/test";

// Live e2e suite: drives the app against the actual packaged Spring Boot JAR
// (real backend, real network, no mocking) on port 8080. Build the JAR first
// with `mvn clean package` from the repo root before running this config.
//
// https://playwright.dev/docs/test-configuration
export default defineConfig({
    testDir: "./e2e-live",
    timeout: 300000,
    expect: {
        timeout: 15000,
    },
    fullyParallel: true,
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 2 : 0,
    reporter: "html",
    use: {
        baseURL: "http://localhost:8080",
        trace: "retain-on-failure",
        video: "retain-on-failure",
    },
    projects: [
        {
            name: "chromium",
            use: {...devices["Desktop Chrome"]},
        },
    ],
    webServer: {
        command: "java -jar ../target/battleship-0.0.1-SNAPSHOT.jar",
        url: "http://localhost:8080",
        timeout: 60000,
        reuseExistingServer: !process.env.CI,
    },
});
