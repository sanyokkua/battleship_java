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

const isMockAdapterMode = import.meta.env.VITE_ADAPTER === 'mock';

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
