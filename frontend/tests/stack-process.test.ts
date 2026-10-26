import { spawn } from 'node:child_process';
import { waitForExit } from '../../scripts/stack.mjs';

describe('owned child shutdown', () => {
    it('waits for actual close after escalating to SIGKILL', async () => {
        const child = spawn(
            process.execPath,
            ['-e', "process.on('SIGTERM', () => {}); process.stdout.write('READY\\n'); setInterval(() => {}, 1000)"],
            { stdio: ['ignore', 'pipe', 'ignore'] },
        );
        await new Promise<void>((resolve) => {
            child.stdout.once('data', () => {
                resolve();
            });
        });
        let closed = false;
        child.once('close', () => {
            closed = true;
        });

        await waitForExit(child, 25);

        expect(child.signalCode).toBe('SIGKILL');
        expect(closed).toBe(true);
    });

    it('returns immediately when a spawn error already closed the child', async () => {
        const child = spawn('/path/that/does/not/exist/t005-child', [], { stdio: 'ignore' });
        let spawnFailed = false;
        child.on('error', () => {
            spawnFailed = true;
        });
        await new Promise<void>((resolve) => {
            child.once('close', () => {
                resolve();
            });
        });
        expect(spawnFailed).toBe(true);
        const started = performance.now();

        await waitForExit(child, 250);

        expect(performance.now() - started).toBeLessThan(200);
    });
});
