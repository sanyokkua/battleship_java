import { spawn } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { createConnection } from 'node:net';
import { fileURLToPath } from 'node:url';
import { type APIResponse, type Page } from '@playwright/test';
import { isStackReady } from './helpers/stack-ready.js';
import { expect, test } from './fixtures/stack.js';

const root = fileURLToPath(new URL('../..', import.meta.url));
interface TestStack {
    origin: string;
    stop(): Promise<void>;
}

let beforeAllStack: TestStack | undefined;
let beforeAllUseStack:
    ((name: string, ports: { frontendPort: number; backendPort: number }) => Promise<TestStack>) | undefined;

test.beforeAll(async ({ useStack }) => {
    beforeAllUseStack = useStack;
    await verifyLocalProdWrapper();
    beforeAllStack = await useStack('before-all', { frontendPort: 4182, backendPort: 8087 });
});

test.afterAll(async () => {
    await beforeAllStack?.stop();
    await expect.poll(async () => Promise.all([isListening(4182), isListening(8087)])).toEqual([false, false]);
});

function isListening(port: number) {
    return new Promise<boolean>((resolve) => {
        const socket = createConnection({ host: '127.0.0.1', port });
        socket.setTimeout(500, () => {
            socket.destroy();
            resolve(false);
        });
        socket.once('connect', () => {
            socket.destroy();
            resolve(true);
        });
        socket.once('error', () => {
            resolve(false);
        });
    });
}

async function waitForExit(child: ReturnType<typeof spawn>) {
    if (child.exitCode !== null || child.signalCode !== null) return;
    await new Promise<void>((resolve) => {
        child.once('exit', () => {
            resolve();
        });
    });
}

async function verifyLocalProdWrapper() {
    const frontendPort = 4181;
    const backendPort = 8086;
    const origin = `http://localhost:${String(frontendPort)}`;
    const child = spawn(`${root}/scripts/local-prod.sh`, [], {
        cwd: '/tmp',
        env: {
            ...process.env,
            BATTLESHIP_E2E_USE_PREBUILT: '1',
            FRONTEND_PORT: String(frontendPort),
            BACKEND_PORT: String(backendPort),
        },
        stdio: ['ignore', 'pipe', 'pipe'],
    });
    let output = '';
    child.stdout.setEncoding('utf8').on('data', (chunk: string) => {
        output += chunk;
    });
    child.stderr.setEncoding('utf8').on('data', (chunk: string) => {
        output += chunk;
    });
    try {
        await expect.poll(() => output, { timeout: 70_000 }).toContain(`[stack] ready at ${origin}`);
        const health = await fetch(`${origin}/api/v1/health`);
        expect(health.status).toBe(200);
        const body: unknown = await health.json();
        expect(body).toMatchObject({ live: true, ready: true });
        child.kill('SIGINT');
        await waitForExit(child);
        expect(child.exitCode).toBe(0);
        await expect
            .poll(async () => Promise.all([isListening(frontendPort), isListening(backendPort)]))
            .toEqual([false, false]);
    } finally {
        if (child.exitCode === null && child.signalCode === null) {
            child.kill('SIGINT');
            await waitForExit(child);
        }
    }
}

test('a dedicated stack helper can be reused between beforeAll and tests', ({ useStack }) => {
    expect(useStack).toBe(beforeAllUseStack);
    const stack = beforeAllStack;
    if (!stack) throw new Error('beforeAll did not start its dedicated stack.');
    expect(stack.origin).toBe('http://localhost:4182');
});

async function createGame(page: Page, origin: string) {
    const meta = await page.request.get(`${origin}/api/v1/meta`);
    expect(meta.ok()).toBeTruthy();
    const csrf = (await page.context().cookies(origin)).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
    expect(csrf).toBeTruthy();
    const response = await page.request.post(`${origin}/api/v1/games`, {
        headers: { Cookie: `XSRF-TOKEN=${csrf ?? ''}`, 'X-XSRF-TOKEN': csrf ?? '', Origin: origin },
        data: { rulesetId: 'sea-battle-10-ship.v1', displayName: 'Smoke test' },
    });
    expect(response.status()).toBe(201);
    return readJson<{ gameId: string }>(response);
}

async function readJson<T>(response: APIResponse): Promise<T> {
    const body: unknown = await response.json();
    return body as T;
}

async function firstSnapshot(page: Page, gameId: string) {
    return page.evaluate<{ phase: string }, { id: string }>(
        ({ id }) =>
            new Promise<{ phase: string }>((resolve, reject) => {
                const stream = new window.EventSource(`/api/v1/games/${id}/events`);
                const timeout = window.setTimeout(() => {
                    stream.close();
                    reject(new Error('snapshot event timed out'));
                }, 10_000);
                stream.addEventListener('snapshot', (event) => {
                    window.clearTimeout(timeout);
                    stream.close();
                    const data: unknown = JSON.parse((event as MessageEvent<string>).data);
                    resolve(data as { phase: string });
                });
                stream.onerror = () => {
                    window.clearTimeout(timeout);
                    stream.close();
                    reject(new Error('event stream failed'));
                };
            }),
        { id: gameId },
    );
}

test('preview serves the shell and proxies health, game snapshots, and deep links', async ({ page, stack }) => {
    await page.goto(stack.origin);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    const health = await page.request.get(`${stack.origin}/api/v1/health`);
    expect(health.status()).toBe(200);
    expect(await readJson<{ live: boolean; ready: boolean }>(health)).toMatchObject({ live: true, ready: true });

    const game = await createGame(page, stack.origin);
    const snapshot = await firstSnapshot(page, game.gameId);
    expect(snapshot.phase).toBe('WAITING');

    for (const path of ['/games/abc', '/join/abc']) {
        const response = await page.request.get(`${stack.origin}${path}`);
        expect(response.status()).toBe(200);
        expect(await response.text()).toContain('<div id="root"></div>');
    }

    const problemResponse = await page.request.get(`${stack.origin}/api/v1/games/not-a-game`);
    const problem = await readJson<{ correlationId: string }>(problemResponse);
    expect(problemResponse.status()).toBe(404);
    expect(problem.correlationId).toMatch(/^[0-9a-f]{16}$/);
    await expect.poll(async () => readFile(stack.logPath, 'utf8')).toContain(`correlationId=${problem.correlationId}`);
});

test('an occupied frontend port gives an actionable diagnostic', async ({ stack, useStack }) => {
    expect(stack.origin).toContain(':4173');
    await expect(useStack('occupied-attempt', { frontendPort: 4173, backendPort: 8082 })).rejects.toThrow(
        /frontend.*4173.*lsof -i :4173/i,
    );
});

test('SIGINT during startup stops the backend child and releases its port', async () => {
    const frontendPort = 5188;
    const backendPort = 8188;
    const child = spawn(process.execPath, ['scripts/stack.mjs', 'dev'], {
        cwd: root,
        env: { ...process.env, FRONTEND_PORT: String(frontendPort), BACKEND_PORT: String(backendPort) },
        stdio: ['ignore', 'pipe', 'pipe'],
    });
    let output = '';
    child.stdout.setEncoding('utf8').on('data', (chunk: string) => {
        output += chunk;
    });
    child.stderr.setEncoding('utf8').on('data', (chunk: string) => {
        output += chunk;
    });

    try {
        await expect.poll(() => output, { timeout: 10_000 }).toContain('[backend] {');
        child.kill('SIGINT');
        await new Promise<void>((resolve) => {
            child.once('exit', () => {
                resolve();
            });
            setTimeout(() => {
                resolve();
            }, 15_000).unref();
        });
        expect(child.exitCode).toBe(0);
        await expect
            .poll(async () => Promise.all([isListening(frontendPort), isListening(backendPort)]))
            .toEqual([false, false]);
    } finally {
        if (child.exitCode === null && child.signalCode === null) child.kill('SIGKILL');
        const backendPid = /"pid":(\d+)/.exec(output)?.[1];
        if (backendPid !== undefined) {
            try {
                process.kill(Number(backendPid), 'SIGTERM');
            } catch {
                // The child has already exited.
            }
        }
    }
});

for (const mode of [
    { name: 'dev', frontendPort: 5174, backendPort: 8084, cliMode: 'dev' },
    { name: 'prod', frontendPort: 4177, backendPort: 8085, cliMode: 'prod' },
]) {
    test(`the ${mode.name} CLI starts, proxies, streams, and stops only its children`, async ({ page }) => {
        const child = spawn(process.execPath, ['scripts/stack.mjs', mode.cliMode], {
            cwd: root,
            env: {
                ...process.env,
                FRONTEND_PORT: String(mode.frontendPort),
                BACKEND_PORT: String(mode.backendPort),
            },
            stdio: ['ignore', 'pipe', 'pipe'],
        });
        let output = '';
        child.stdout.setEncoding('utf8').on('data', (chunk: string) => (output += chunk));
        child.stderr.setEncoding('utf8').on('data', (chunk: string) => (output += chunk));
        const origin = `http://localhost:${String(mode.frontendPort)}`;
        try {
            await expect.poll(() => isStackReady(output, origin), { timeout: 70_000 }).toBe(true);
            const health = await page.request.get(`${origin}/api/v1/health`);
            expect(await readJson<{ live: boolean; ready: boolean }>(health)).toMatchObject({
                live: true,
                ready: true,
            });
            await page.goto(origin);
            const game = await createGame(page, origin);
            expect((await firstSnapshot(page, game.gameId)).phase).toBe('WAITING');
        } finally {
            child.kill('SIGINT');
            await new Promise<void>((resolve) => {
                if (child.exitCode !== null) {
                    resolve();
                    return;
                }
                child.once('exit', () => {
                    resolve();
                });
                setTimeout(() => {
                    resolve();
                }, 10_000).unref();
            });
        }
        expect(child.exitCode).toBe(0);
        expect(output).toMatch(/stopped|shutdown|ready/i);
        await expect
            .poll(async () => Promise.all([isListening(mode.frontendPort), isListening(mode.backendPort)]))
            .toEqual([false, false]);
    });
}
