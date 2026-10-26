import eslint from '@eslint/js';
import prettier from 'eslint-config-prettier';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

export default tseslint.config(
    {
        ignores: ['dist', 'coverage', 'playwright-report', 'test-results', 'node_modules', 'src/contract/generated'],
    },
    eslint.configs.recommended,
    {
        files: ['**/*.{ts,tsx}'],
        extends: [tseslint.configs.strictTypeChecked, tseslint.configs.stylisticTypeChecked],
        languageOptions: {
            parserOptions: {
                projectService: true,
                tsconfigRootDir: import.meta.dirname,
            },
        },
        plugins: { 'react-hooks': reactHooks },
        rules: {
            ...reactHooks.configs.recommended.rules,
            'no-restricted-globals': [
                'error',
                { name: 'fetch', message: 'Use the gateway for HTTP requests.' },
                { name: 'EventSource', message: 'Use the gateway for event streams.' },
                { name: 'localStorage', message: 'Use the preferences storage gateway.' },
            ],
        },
    },
    {
        files: ['scripts/**/*.mjs'],
        languageOptions: {
            globals: { console: 'readonly', process: 'readonly' },
        },
    },
    {
        files: ['src/gateway/**/*.{ts,tsx}', 'src/preferences/**/*.{ts,tsx}'],
        rules: { 'no-restricted-globals': 'off' },
    },
    prettier,
);
