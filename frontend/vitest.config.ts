import react from "@vitejs/plugin-react";
import {defineConfig} from "vitest/config";

// https://vitest.dev/config/
export default defineConfig({
    plugins: [react()],
    test: {
        environment: "jsdom",
        setupFiles: ["./src/setupTests.ts"],
        globals: true,
        passWithNoTests: true,
        coverage: {
            provider: "v8",
            reporter: ["text", "html", "lcov"],
            include: ["src/**/*.{ts,tsx}"],
            exclude: [
                "**/*.test.{ts,tsx}",
                "src/setupTests.ts",
                "src/**/*.d.ts",
                "src/vite-env.d.ts",
                "src/**/*.css",
            ],
            // Percentage gates apply only to the areas TESTING_PLAN.md §3 calls out
            // (adapters/hooks/utils). Screens/widgets/design are gated instead by
            // "every screen + every cell state has >=1 test" (a presence check), not
            // a coverage percentage — do not add thresholds for those here.
            thresholds: {
                "src/adapters/**": {lines: 90},
                "src/hooks/**": {lines: 90},
                "src/utils/**": {lines: 90},
            },
        },
    },
});
