import type { GameAdapter } from '../../src/adapters/GameAdapter';
import type * as GameBrowserStorage from '../../src/services/GameBrowserStorage';

// Mirrors the `E2EMockHooks` type declared in src/App.tsx. Duplicated (rather than
// imported) on purpose: App.tsx's `declare global` augments the same ambient `Window`
// interface, but this file needs its own `export {}` to be treated as a module by the
// e2e TS project (which does not include App.tsx, a .tsx/React file, in its program).
declare global {
  interface Window {
    __e2eMockHooks?: {
      adapter: GameAdapter;
      storage: typeof GameBrowserStorage;
    };
  }
}

export {};
