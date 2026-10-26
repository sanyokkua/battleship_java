import type { Browser, BrowserContext } from '@playwright/test';

export interface StackOptions {
    name: string;
    frontendPort: number;
    backendPort: number;
    mode: 'dev' | 'preview';
    env?: Record<string, string>;
}

export interface StackHandle {
    origin: string;
    logPath: string;
    stop(): Promise<void>;
    restartBackend(): Promise<void>;
}

export interface TwoPlayers {
    host: BrowserContext;
    guest: BrowserContext;
    close(): Promise<void>;
}

export function startStack(options: StackOptions): Promise<StackHandle>;
export function redact(line: string): string;
export function twoPlayers(browser: Browser): Promise<TwoPlayers>;
