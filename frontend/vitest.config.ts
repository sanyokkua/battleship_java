import { defineConfig, mergeConfig } from 'vitest/config';
import viteConfig from './vite.config';

export default mergeConfig(
    viteConfig,
    defineConfig({
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
    }),
);
