import {createContext, type ReactNode, useContext} from "react";
import type {GameAdapter} from "./GameAdapter";

/**
 * React context carrying the active {@link GameAdapter} implementation.
 * `null` by default so unwrapped access fails fast via {@link useGameAdapter}
 * rather than silently working with a missing adapter.
 */
const GameAdapterContext = createContext<GameAdapter | null>(null);

/**
 * Supplies a {@link GameAdapter} implementation to the component tree.
 * Mount once near the app root with `HttpGameAdapter` for real backend use,
 * or `MockGameAdapter` for tests / `npm run dev:mock`.
 *
 * @param adapter - The GameAdapter implementation to expose to descendants.
 * @param children - The subtree that may call {@link useGameAdapter}.
 */
export function GameAdapterProvider({adapter, children}: { adapter: GameAdapter; children: ReactNode }) {
    return (
        <GameAdapterContext.Provider value={adapter}>
            {children}
        </GameAdapterContext.Provider>
    );
}

/**
 * Retrieves the {@link GameAdapter} supplied by the nearest {@link GameAdapterProvider}.
 * This is the only sanctioned way for widgets/screens/hooks to reach the adapter —
 * never call the network or a concrete adapter implementation directly.
 *
 * @returns The active GameAdapter implementation.
 * @throws Error if called outside a {@link GameAdapterProvider}.
 */
export function useGameAdapter(): GameAdapter {
    const ctx = useContext(GameAdapterContext);
    if (!ctx) {
        throw new Error("useGameAdapter must be used within a GameAdapterProvider");
    }
    return ctx;
}
