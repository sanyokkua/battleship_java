import react from "@vitejs/plugin-react";
import {defineConfig, loadEnv} from "vite";
import {normalizeAppBasePath, toViteAssetBasePath} from "./src/config/appBasePath";

// https://vitejs.dev/config/
export default defineConfig(({mode}) => {
    const env = loadEnv(mode, process.cwd(), "");
    const normalizedBasePath = normalizeAppBasePath(env.VITE_APP_BASE_PATH);

    return {
        base: toViteAssetBasePath(normalizedBasePath),
        plugins: [react()],
        server: {
            proxy: {
                "/api": {
                    target: "http://localhost:8080",
                    changeOrigin: true,
                },
            },
        },
        build: {
            outDir: "build",
        },
    };
});
