import { defineConfig } from '@playwright/test';

// The journeys start their own backend + preview stacks (see e2e/fixtures/stack.ts); the default one is on 4173.
export default defineConfig({
    testDir: './e2e',
    fullyParallel: false,
    workers: 1,
    use: {
        browserName: 'chromium',
        baseURL: 'http://localhost:4173',
    },
});
