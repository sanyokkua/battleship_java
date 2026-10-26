import { test as base, expect } from '@playwright/test';
import { startStack, twoPlayers, type StackHandle } from '../../../scripts/stack.mjs';

interface Ports {
    frontendPort: number;
    backendPort: number;
}

interface StackFixtures {
    stack: StackHandle;
    twoPlayers: Awaited<ReturnType<typeof twoPlayers>>;
}

interface WorkerFixtures {
    defaultStack: StackHandle;
    useStack: (name: string, ports: Ports, env?: Record<string, string>) => Promise<StackHandle>;
}

const highRateLimits = {
    BATTLESHIP_RATELIMIT_CREATEGAMEPERMINUTE: '1000',
    BATTLESHIP_RATELIMIT_JOINPERMINUTE: '1000',
    BATTLESHIP_RATELIMIT_COMMANDSPERMINUTE: '1000',
    BATTLESHIP_RATELIMIT_READGAMEPERMINUTE: '1000',
    BATTLESHIP_RATELIMIT_STREAMOPENPERMINUTE: '1000',
};

export const test = base.extend<StackFixtures, WorkerFixtures>({
    defaultStack: [
        async ({}, use) => {
            const stack = await startStack({
                name: 'default',
                frontendPort: 4173,
                backendPort: 8080,
                mode: 'preview',
                env: highRateLimits,
            });
            try {
                await use(stack);
            } finally {
                await stack.stop();
            }
        },
        { scope: 'worker' },
    ],
    stack: async ({ defaultStack }, use) => {
        await use(defaultStack);
    },
    useStack: [
        async ({}, use) => {
            await use((name, ports, env = {}) =>
                startStack({ name, ...ports, mode: 'preview', env: { ...highRateLimits, ...env } }),
            );
        },
        { scope: 'worker' },
    ],
    twoPlayers: async ({ browser }, use) => {
        const players = await twoPlayers(browser);
        try {
            await use(players);
        } finally {
            await players.close();
        }
    },
});

export { expect };
