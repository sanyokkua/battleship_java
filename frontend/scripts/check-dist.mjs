import { readdir, readFile } from 'node:fs/promises';
import { join, relative, resolve } from 'node:path';

const dist = resolve('dist');
const forbidden = /localhost:|127\.0\.0\.1|:8080/i;
const violations = [];

async function inspect(directory) {
    for (const entry of await readdir(directory, { withFileTypes: true })) {
        const path = join(directory, entry.name);
        if (entry.isDirectory()) {
            await inspect(path);
        } else {
            const content = await readFile(path, 'utf8');
            const match = forbidden.exec(content);
            if (match) violations.push(`${relative(process.cwd(), path)} contains ${match[0]}`);
        }
    }
}

try {
    await inspect(dist);
} catch (error) {
    if (error.code !== 'ENOENT') throw error;
}

if (violations.length > 0) {
    console.error('Built frontend contains a development address:');
    for (const violation of violations) console.error(`  ${violation}`);
    process.exitCode = 1;
}
