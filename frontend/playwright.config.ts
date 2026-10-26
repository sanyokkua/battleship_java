import { defineConfig } from '@playwright/test';

export default defineConfig({
    testDir: './e2e',
    fullyParallel: false,
    workers: 1,
    use: {
        browserName: 'chromium',
        baseURL: 'http://localhost:4173',
    },
});
