import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';

const backendPort = Number(process.env.BACKEND_PORT ?? 8080);
const devPort = Number(process.env.FRONTEND_PORT ?? 5173);
const previewPort = Number(process.env.FRONTEND_PORT ?? 4173);
const apiProxy = { '/api': { target: `http://127.0.0.1:${String(backendPort)}` } };

export default defineConfig({
    plugins: [
        react(),
        VitePWA({
            registerType: 'prompt',
            injectRegister: false,
            manifest: false,
            workbox: {
                navigateFallback: 'index.html',
                navigateFallbackDenylist: [/^\/api\//],
                cleanupOutdatedCaches: true,
            },
        }),
    ],
    server: { host: 'localhost', port: devPort, strictPort: true, proxy: apiProxy },
    preview: { host: 'localhost', port: previewPort, strictPort: true, proxy: apiProxy },
});
