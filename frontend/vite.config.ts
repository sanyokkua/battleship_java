import react from '@vitejs/plugin-react';
import { readFileSync } from 'node:fs';
import { defineConfig, type ProxyOptions } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';

// Ports live here and nowhere else in the frontend: the e2e fixture, scripts/local-deploy.sh and the dev server read
// these variables. `vite preview` is the packaged, same-origin local server (static files plus the /api proxy, so the
// browser never makes a cross-origin call and the backend needs no CORS).
const backendPort = Number(process.env.BACKEND_PORT ?? 8080);
const previewPort = Number(process.env.FRONTEND_PORT ?? 4173);
// local-deploy.sh sets these for LAN use: the backend's session cookie is Secure, so a LAN origin needs HTTPS.
const tlsCert = process.env.FRONTEND_TLS_CERT;
const tlsKey = process.env.FRONTEND_TLS_KEY;

const apiProxy: Record<string, ProxyOptions> = {
    '/api': {
        target: `http://127.0.0.1:${String(backendPort)}`,
        // Replace, never append: a client-supplied X-Forwarded-For must not reach the backend's per-client limits.
        configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq, req) => {
                proxyReq.setHeader('X-Forwarded-For', req.socket.remoteAddress ?? '');
            });
        },
    },
};

// Keep in step with config/nginx-server.conf.template (the nginx images); the reasons are written there.
const securityHeaders = {
    'Content-Security-Policy': [
        "default-src 'self'",
        "style-src 'self'",
        "style-src-attr 'unsafe-inline'",
        "img-src 'self' data:",
        "object-src 'none'",
        "base-uri 'none'",
        "form-action 'self'",
        "frame-ancestors 'none'",
    ].join('; '),
    'Referrer-Policy': 'no-referrer',
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
};

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
    build: {
        sourcemap: process.env.BROWSER_COVERAGE === '1' ? 'hidden' : false,
        ...(process.env.BROWSER_COVERAGE === '1' ? { minify: false } : {}),
    },
    // `npm run dev` (hot reload against a separately started backend on BACKEND_PORT).
    server: { host: 'localhost', proxy: apiProxy },
    preview: {
        // An IPv4 literal: the name `localhost` can bind IPv6 only, which clients that try IPv4 (Node's fetch in some
        // containers) cannot reach. Browsers still open http://localhost:<port> and fall back to IPv4.
        host: process.env.FRONTEND_HOST ?? '127.0.0.1',
        port: previewPort,
        strictPort: true,
        proxy: apiProxy,
        headers: securityHeaders,
        ...(tlsCert && tlsKey ? { https: { cert: readFileSync(tlsCert), key: readFileSync(tlsKey) } } : {}),
    },
});
