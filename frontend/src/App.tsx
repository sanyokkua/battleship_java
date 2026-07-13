import {useEffect, useMemo} from 'react';
import {HttpGameAdapter} from './adapters/HttpGameAdapter';
import {MockGameAdapter} from './adapters/MockGameAdapter';
import type {GameAdapter} from './adapters/GameAdapter';
import {GameAdapterProvider} from './adapters/GameAdapterContext';
import {ToastProvider} from './widgets/feedback/ToastContext';
import {ToastStack} from './widgets/feedback/ToastStack';
import {AppBar} from './widgets/layout/AppBar';
import {AppRoutes} from './routing/AppRoutes';
import * as GameBrowserStorage from './services/GameBrowserStorage';

/**
 * Test-only hooks exposed on `window` when running with the mock adapter
 * (VITE_ADAPTER=mock, see .env.mock / `npm run dev:mock`) so Playwright e2e specs
 * can drive a second "opponent" player via page.evaluate() without a second
 * browser tab: each tab has its own independent JS module graph and thus its own
 * independent MockGameAdapter instance, so there is no cross-tab state sharing.
 * Never populated in production builds/default dev mode.
 */
type E2EMockHooks = {
    adapter: GameAdapter;
    storage: typeof GameBrowserStorage;
};

declare global {
    interface Window {
        __e2eMockHooks?: E2EMockHooks;
    }
}

/**
 * Whether the app is running against `MockGameAdapter` instead of the real backend.
 * Controlled by the `VITE_ADAPTER` build-time env var (set to `mock` by `.env.mock`
 * / `npm run dev:mock`); read once at module load since it never changes at runtime.
 */
const isMockAdapterMode = import.meta.env.VITE_ADAPTER === 'mock';

/**
 * Application root component.
 *
 * Selects the {@link GameAdapter} implementation for the whole app — `MockGameAdapter`
 * when {@link isMockAdapterMode} is true, `HttpGameAdapter` otherwise — and provides it
 * to the tree via {@link GameAdapterProvider}, alongside toast notifications and the
 * top-level layout ({@link AppBar}, {@link AppRoutes}, {@link ToastStack}).
 *
 * In mock mode, also exposes the adapter and browser storage helpers on
 * `window.__e2eMockHooks` (see {@link E2EMockHooks}) for Playwright e2e specs.
 */
function App() {
    const adapter = useMemo<GameAdapter>(
        () => (isMockAdapterMode ? new MockGameAdapter() : new HttpGameAdapter()),
        [],
    );

    // Side effect (mutating `window`) belongs in an effect, not in the useMemo above.
    useEffect(() => {
        if (isMockAdapterMode) {
            window.__e2eMockHooks = {adapter, storage: GameBrowserStorage};
        }
    }, [adapter]);

    return (
        <GameAdapterProvider adapter={adapter}>
            <ToastProvider>
                <AppBar/>
                <AppRoutes/>
                <ToastStack/>
            </ToastProvider>
        </GameAdapterProvider>
    );
}

export default App;
