import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
    plugins: [react()],
    test: {
        environment: 'jsdom',
        environmentOptions: { jsdom: { url: 'http://localhost/' } },
        setupFiles: ['./tests/setup.ts'],
        exclude: ['e2e/**', 'node_modules/**'],
        globals: true,
        css: true,
        reporters: ['default', ['vitest-sonar-reporter', { outputFile: 'coverage/sonar-execution.xml' }]],
        coverage: {
            provider: 'v8',
            reporter: ['text', 'lcov'],
            include: ['src/**/*.{ts,tsx}'],
            exclude: ['src/contract/generated/**', 'src/**/*.d.ts'],
        },
    },
});
