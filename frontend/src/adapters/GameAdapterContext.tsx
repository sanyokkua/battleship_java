import {createContext, type ReactNode, useContext} from "react";
import type {GameAdapter} from "./GameAdapter";

const GameAdapterContext = createContext<GameAdapter | null>(null);

export function GameAdapterProvider({adapter, children}: { adapter: GameAdapter; children: ReactNode }) {
    return (
        <GameAdapterContext.Provider value={adapter}>
            {children}
        </GameAdapterContext.Provider>
    );
}

export function useGameAdapter(): GameAdapter {
    const ctx = useContext(GameAdapterContext);
    if (!ctx) {
        throw new Error("useGameAdapter must be used within a GameAdapterProvider");
    }
    return ctx;
}
