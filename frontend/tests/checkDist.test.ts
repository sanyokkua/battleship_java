import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { afterEach, beforeEach, expect, it } from 'vitest';

const script = resolve('scripts/check-dist.mjs');
let cwd: string;

beforeEach(() => {
    cwd = mkdtempSync(join(tmpdir(), 'check-dist-'));
});
afterEach(() => {
    rmSync(cwd, { recursive: true, force: true });
});

const run = () => spawnSync(process.execPath, [script], { cwd, encoding: 'utf8' });

it('fails when dist is missing, so an unbuilt app cannot pass the check', () => {
    expect(run().status).toBe(1);
});

it('passes a dist without development addresses and fails one that contains them', () => {
    mkdirSync(join(cwd, 'dist'));
    writeFileSync(join(cwd, 'dist', 'app.js'), 'fetch("/api/v1/meta")');
    expect(run().status).toBe(0);

    writeFileSync(join(cwd, 'dist', 'app.js'), 'fetch("http://localhost:8080/api")');
    expect(run().status).toBe(1);
});
