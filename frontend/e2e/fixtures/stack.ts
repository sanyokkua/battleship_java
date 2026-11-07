import { test as base, expect } from '@playwright/test';
import { type ChildProcess, spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { createWriteStream } from 'node:fs';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { setTimeout as sleep } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';

// A "stack" is the packaged backend JAR plus `vite preview` (static files and the /api proxy, one origin), the same
// shape scripts/local-deploy.sh runs. Each stack gets its own ports so journeys can use different backend settings.
const frontendDir = fileURLToPath(new URL('../..', import.meta.url));
const jar = resolve(frontendDir, '../backend/app/target/battleship-app.jar');

export interface StackHandle {
    origin: string;
    logPath: string;
    stop(): Promise<void>;
    restartBackend(): Promise<void>;
}

interface Ports {
    frontendPort: number;
    backendPort: number;
}

interface StackFixtures {
    stack: StackHandle;
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

function exited(child: ChildProcess) {
    return child.exitCode !== null || child.signalCode !== null;
}

async function stopProcess(child: ChildProcess) {
    if (exited(child)) return;
    const closed = new Promise<void>((done) => {
        child.once('close', () => {
            done();
        });
    });
    child.kill('SIGTERM');
    const timer = setTimeout(() => child.kill('SIGKILL'), 5_000);
    await closed;
    clearTimeout(timer);
}

async function waitUntilServing(url: string, child: ChildProcess, what: string) {
    for (let attempt = 0; attempt < 240; attempt++) {
        if (exited(child)) throw new Error(`${what} exited before it was ready at ${url}; see the stack log.`);
        try {
            const response = await fetch(url, { signal: AbortSignal.timeout(1_000) });
            if (response.ok) return;
        } catch {
            // Still starting.
        }
        await sleep(250);
    }
    throw new Error(`${what} was not ready at ${url} after 60 seconds.`);
}

export async function startStack(
    name: string,
    { frontendPort, backendPort }: Ports,
    env: Record<string, string> = {},
): Promise<StackHandle> {
    const origin = `http://localhost:${String(frontendPort)}`;
    const logPath = resolve(frontendDir, 'e2e/.logs', `${name}.log`);
    await mkdir(resolve(frontendDir, 'e2e/.logs'), { recursive: true });
    const log = createWriteStream(logPath);
    const run = (command: string, args: string[], extraEnv: Record<string, string>) => {
        const child = spawn(command, args, {
            cwd: frontendDir,
            env: { ...process.env, ...extraEnv },
            stdio: ['ignore', 'pipe', 'pipe'],
        });
        child.stdout.pipe(log, { end: false });
        child.stderr.pipe(log, { end: false });
        return child;
    };
    const startBackend = async () => {
        const child = run('java', ['-jar', jar], {
            ...env,
            SERVER_PORT: String(backendPort),
            BATTLESHIP_PUBLICBASEURL: origin,
        });
        try {
            await waitUntilServing(`http://127.0.0.1:${String(backendPort)}/api/v1/health`, child, 'backend');
        } catch (error) {
            await stopProcess(child);
            throw error;
        }
        return child;
    };

    let backend = await startBackend();
    const frontend = run(process.execPath, ['node_modules/vite/bin/vite.js', 'preview'], {
        FRONTEND_PORT: String(frontendPort),
        BACKEND_PORT: String(backendPort),
    });
    try {
        await waitUntilServing(`http://127.0.0.1:${String(frontendPort)}`, frontend, 'frontend');
    } catch (error) {
        await Promise.all([stopProcess(frontend), stopProcess(backend)]);
        throw error;
    }

    return {
        origin,
        logPath,
        async stop() {
            await Promise.all([stopProcess(frontend), stopProcess(backend)]);
            log.end();
        },
        async restartBackend() {
            await stopProcess(backend);
            backend = await startBackend();
        },
    };
}

export const test = base.extend<StackFixtures, WorkerFixtures>({
    page: async ({ page }, use, testInfo) => {
        if (process.env.BROWSER_COVERAGE !== '1') {
            await use(page);
            return;
        }
        await page.coverage.startJSCoverage({ resetOnNavigation: false });
        try {
            await use(page);
        } finally {
            const entries = (await page.coverage.stopJSCoverage()).filter(
                (entry) => entry.url.startsWith('http') && new URL(entry.url).pathname.startsWith('/assets/'),
            );
            const directory = resolve(process.cwd(), 'coverage/browser');
            await mkdir(directory, { recursive: true });
            const name = createHash('sha256')
                .update(`${testInfo.testId}:${String(testInfo.retry)}`)
                .digest('hex');
            await writeFile(resolve(directory, `${name}.json`), JSON.stringify(entries));
        }
    },
    defaultStack: [
        async ({}, use) => {
            const stack = await startStack('default', { frontendPort: 4173, backendPort: 8080 }, highRateLimits);
            try {
                await use(stack);
            } finally {
                await stack.stop();
            }
        },
        // Starting a JVM and a web server can take longer than a test's 30 seconds on a busy or containerised machine.
        { scope: 'worker', timeout: 120_000 },
    ],
    stack: async ({ defaultStack }, use) => {
        await use(defaultStack);
    },
    useStack: [
        async ({}, use) => {
            await use((name, ports, env = {}) => startStack(name, ports, { ...highRateLimits, ...env }));
        },
        { scope: 'worker' },
    ],
});

export { expect };
