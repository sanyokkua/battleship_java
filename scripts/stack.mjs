import { spawn } from 'node:child_process';
import { mkdir, appendFile } from 'node:fs/promises';
import net from 'node:net';
import { dirname, resolve, basename } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const jar = resolve(root, 'backend/app/target/battleship-app-1.0.0-SNAPSHOT.jar');
const frontendDir = resolve(root, 'frontend');

const delay = (ms) => new Promise((resolveDelay) => setTimeout(resolveDelay, ms));

function safeName(name) {
    return basename(name).replace(/[^a-zA-Z0-9_-]/g, '_') || 'stack';
}

function writeLine(stream, line) {
    stream.write(`${line}\n`);
}

function childLines(child, prefix, output) {
    let pending = '';
    const flush = (chunk) => {
        pending += chunk;
        const lines = pending.split(/\r?\n/);
        pending = lines.pop() ?? '';
        for (const line of lines) output(`${prefix} ${redact(line)}`);
    };
    child.stdout?.setEncoding('utf8').on('data', flush);
    child.stderr?.setEncoding('utf8').on('data', flush);
    child.once('close', () => {
        if (pending) output(`${prefix} ${redact(pending)}`);
    });
}

function waitForExit(child, timeoutMs = 5_000) {
    if (child.exitCode !== null || child.signalCode !== null) return Promise.resolve();
    return new Promise((resolveExit) => {
        const timer = setTimeout(() => {
            child.kill('SIGKILL');
            resolveExit();
        }, timeoutMs);
        timer.unref();
        child.once('close', () => {
            clearTimeout(timer);
            resolveExit();
        });
        child.kill('SIGTERM');
    });
}

async function portAvailable(port) {
    return new Promise((resolvePort, rejectPort) => {
        const server = net.createServer();
        server.once('error', (error) => {
            if (error.code === 'EADDRINUSE') resolvePort(false);
            else rejectPort(error);
        });
        server.listen(port, '127.0.0.1', () => server.close(() => resolvePort(true)));
    });
}

async function assertPortAvailable(component, port) {
    if (!(await portAvailable(port))) {
        throw new Error(`${component} port localhost:${port} is already in use; inspect it with lsof -i :${port}.`);
    }
}

function spawnChild(command, args, options, component, address, output) {
    const child = spawn(command, args, { ...options, stdio: ['ignore', 'pipe', 'pipe'] });
    childLines(child, `[${component}]`, output);
    child.once('error', (error) => output(`[${component}] failed to start at ${address}: ${redact(error.message)}`));
    return child;
}

function waitForChild(child, component, address) {
    return new Promise((resolveChild, rejectChild) => {
        if (child.exitCode !== null || child.signalCode !== null) {
            rejectChild(new Error(`${component} exited before becoming ready at ${address} (exit ${child.exitCode ?? child.signalCode}).`));
            return;
        }
        child.once('error', (error) => rejectChild(new Error(`${component} could not start at ${address}: ${error.message}`)));
        child.once('exit', (code, signal) =>
            rejectChild(new Error(`${component} exited before becoming ready at ${address} (exit ${code ?? signal}).`)),
        );
        resolveChild();
    });
}

async function waitForBackend(port, child, output) {
    const address = `http://127.0.0.1:${port}/api/v1/health`;
    const deadline = Date.now() + 60_000;
    while (Date.now() < deadline) {
        if (child.exitCode !== null || child.signalCode !== null) {
            throw new Error(`backend exited before health was ready at localhost:${port}; inspect the [backend] log and run ./scripts/dev.sh after fixing the cause.`);
        }
        try {
            const response = await fetch(address, { signal: AbortSignal.timeout(1_000) });
            if (response.ok && (await response.json()).ready === true) return;
        } catch {
            // The backend may still be starting; retry until the bounded deadline.
        }
        await delay(250);
    }
    output(`[backend] health did not become ready at localhost:${port} within 60 seconds`);
    throw new Error(`backend did not become ready at localhost:${port}; inspect the [backend] log and run ./scripts/dev.sh after fixing the cause.`);
}

async function waitForFrontend(port, child) {
    const address = `http://localhost:${port}/`;
    const deadline = Date.now() + 60_000;
    while (Date.now() < deadline) {
        if (child.exitCode !== null || child.signalCode !== null) {
            throw new Error(`frontend exited before becoming ready at localhost:${port}; inspect the [frontend] log and run npm --prefix frontend run build.`);
        }
        try {
            const response = await fetch(address, { signal: AbortSignal.timeout(1_000) });
            if (response.ok) return;
        } catch {
            // Vite is still binding its configured port; retry until the bounded deadline.
        }
        await delay(100);
    }
    throw new Error(`frontend did not become ready at localhost:${port}; inspect the [frontend] log and run npm --prefix frontend run build.`);
}

export function redact(line) {
    return line
        .replace(/(Set-Cookie\s*:\s*)[^\r\n]*/gi, '$1[REDACTED]')
        .replace(/(\bCookie\s*:\s*)[^\r\n]*/gi, '$1[REDACTED]')
        .replace(/(#invite=)[^\s&#]+/gi, '$1[REDACTED]')
        .replace(/(XSRF-TOKEN\s*=\s*)[^;\s,]+/gi, '$1[REDACTED]')
        .replace(/(__Host-battleship_session\s*=\s*)[^;\s,]+/gi, '$1[REDACTED]');
}

export async function twoPlayers(browser) {
    const host = await browser.newContext();
    try {
        const guest = await browser.newContext();
        return {
            host,
            guest,
            async close() {
                await Promise.all([host.close(), guest.close()]);
            },
        };
    } catch (error) {
        await host.close();
        throw error;
    }
}

export async function startStack({ name, frontendPort, backendPort, mode, env = {} }) {
    if (!Number.isInteger(frontendPort) || !Number.isInteger(backendPort)) {
        throw new TypeError('frontendPort and backendPort must be integers.');
    }
    if (!['dev', 'preview'].includes(mode)) throw new TypeError(`Unsupported frontend mode: ${mode}`);

    const origin = `http://localhost:${frontendPort}`;
    const logPath = resolve(frontendDir, 'e2e/.logs', `${safeName(name)}.log`);
    await mkdir(dirname(logPath), { recursive: true });
    await appendFile(logPath, '');

    const output = (line) => {
        const safeLine = redact(line);
        writeLine(process.stdout, safeLine);
        void appendFile(logPath, `${safeLine}\n`).catch((error) => {
            writeLine(process.stderr, `[stack] could not append log: ${redact(error.message)}`);
        });
    };

    await assertPortAvailable('frontend', frontendPort);
    await assertPortAvailable('backend', backendPort);

    const childEnv = { ...process.env, ...env };
    const backendEnv = {
        ...childEnv,
        SERVER_PORT: String(backendPort),
        BATTLESHIP_PUBLICBASEURL: origin,
    };
    const viteEnv = {
        ...childEnv,
        FRONTEND_PORT: String(frontendPort),
        BACKEND_PORT: String(backendPort),
    };

    let backend;
    let frontend;
    let stopped = false;

    const stopBackend = async () => {
        if (!backend) return;
        const child = backend;
        backend = undefined;
        await waitForExit(child);
    };

    const startBackend = async () => {
        backend = spawnChild('java', ['-jar', jar], { cwd: root, env: backendEnv }, 'backend', `localhost:${backendPort}`, output);
        await waitForChild(backend, 'backend', `localhost:${backendPort}`);
        await waitForBackend(backendPort, backend, output);
        output(`[backend] ready at localhost:${backendPort}`);
    };

    const stop = async () => {
        if (stopped) return;
        stopped = true;
        if (frontend) {
            const child = frontend;
            frontend = undefined;
            await waitForExit(child);
        }
        await stopBackend();
        output(`[stack] ${safeName(name)} stopped`);
    };

    try {
        await startBackend();
        const vite = resolve(frontendDir, 'node_modules/vite/bin/vite.js');
        const args = mode === 'dev' ? [vite, '--host', 'localhost'] : [vite, 'preview', '--host', 'localhost'];
        const address = `localhost:${frontendPort}`;
        frontend = spawnChild(process.execPath, args, { cwd: frontendDir, env: viteEnv }, 'frontend', address, output);
        await waitForChild(frontend, 'frontend', address);
        await waitForFrontend(frontendPort, frontend);
        output(`[frontend] ready at ${origin}`);
    } catch (error) {
        await stop();
        throw error;
    }

    return {
        origin,
        logPath,
        async stop() {
            await stop();
        },
        async restartBackend() {
            if (stopped) throw new Error(`Cannot restart backend after ${safeName(name)} has stopped.`);
            await stopBackend();
            await startBackend();
        },
    };
}

async function runCli(mode) {
    const frontendPort = Number(process.env.FRONTEND_PORT ?? (mode === 'dev' ? 5173 : 4173));
    const backendPort = Number(process.env.BACKEND_PORT ?? 8080);
    const stack = await startStack({ name: mode, frontendPort, backendPort, mode: mode === 'dev' ? 'dev' : 'preview' });
    writeLine(process.stdout, `[stack] ready at ${stack.origin}`);
    await new Promise((resolveSignal) => {
        let stopping = false;
        const shutdown = async (signal) => {
            if (stopping) return;
            stopping = true;
            try {
                await stack.stop();
                writeLine(process.stdout, `[stack] stopped after ${signal}`);
                process.exitCode = 0;
            } catch (error) {
                writeLine(process.stderr, `[stack] shutdown failed: ${redact(error.message)}`);
                process.exitCode = 1;
            }
            resolveSignal();
        };
        process.once('SIGINT', () => void shutdown('SIGINT'));
        process.once('SIGTERM', () => void shutdown('SIGTERM'));
    });
}

const invokedPath = process.argv[1] && pathToFileURL(resolve(process.argv[1])).href;
if (invokedPath === import.meta.url) {
    const requested = process.argv[2];
    if (!['dev', 'prod'].includes(requested)) {
        writeLine(process.stderr, 'Usage: node scripts/stack.mjs dev|prod');
        process.exitCode = 2;
    } else {
        runCli(requested).catch((error) => {
            writeLine(process.stderr, `[stack] startup failed: ${redact(error.message)}`);
            process.exitCode = 1;
        });
    }
}
