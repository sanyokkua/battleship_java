import { spawn } from 'node:child_process';
import os from 'node:os';

const [secondsValue, command, ...args] = process.argv.slice(2);
const seconds = Number(secondsValue);

if (!Number.isSafeInteger(seconds) || seconds < 1 || !command) {
    process.stderr.write('Usage: node scripts/verify-timeout.mjs <seconds> <command> [args...]\n');
    process.exit(2);
}

const child = spawn(command, args, {
    detached: process.platform !== 'win32',
    stdio: 'inherit',
});
let timedOut = false;
let spawnError;
let killTimer;
const timeout = setTimeout(() => {
    timedOut = true;
    try {
        if (process.platform === 'win32') child.kill('SIGTERM');
        else process.kill(-child.pid, 'SIGTERM');
    } catch {
        child.kill('SIGTERM');
    }
    killTimer = setTimeout(() => {
        try {
            if (process.platform === 'win32') child.kill('SIGKILL');
            else process.kill(-child.pid, 'SIGKILL');
        } catch {
            // The process group may already have exited.
        }
    }, 1000);
}, seconds * 1000);

child.on('error', (error) => {
    spawnError = error;
});
child.on('close', (code, signal) => {
    clearTimeout(timeout);
    if (!timedOut) clearTimeout(killTimer);
    if (spawnError) {
        process.stderr.write(`${spawnError.message}\n`);
        process.exitCode = 127;
    } else if (timedOut) {
        process.exitCode = 124;
    } else if (code !== null) {
        process.exitCode = code;
    } else {
        process.exitCode = signal === null ? 1 : 128 + (os.constants.signals[signal] ?? 1);
    }
});
