import { copyFile, mkdir, readdir, readFile, rm } from 'node:fs/promises';
import { basename, dirname, isAbsolute, relative, resolve, sep } from 'node:path';
import { URL, fileURLToPath, pathToFileURL } from 'node:url';
import convert from 'ast-v8-to-istanbul';
import { createCoverageMap } from '@vitest/istanbul-lib-coverage';
import { create, createContext } from '@vitest/istanbul-lib-report';
import { parseAstAsync } from 'vite';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const coverageDir = resolve(root, 'coverage');
const browserDir = resolve(coverageDir, 'browser');
const buildDir = resolve(coverageDir, 'browser-build');
const sourceDir = resolve(root, 'src');

function appSource(path) {
    const name = relative(sourceDir, path);
    return (
        name !== '' &&
        !name.startsWith(`..${sep}`) &&
        name !== '..' &&
        !isAbsolute(name) &&
        !name.startsWith(`contract${sep}generated${sep}`)
    );
}

async function prepare() {
    await readFile(resolve(coverageDir, 'coverage-final.json'));
    await rm(browserDir, { recursive: true, force: true });
    await rm(buildDir, { recursive: true, force: true });
    await mkdir(browserDir, { recursive: true });
    await rm(resolve(coverageDir, 'lcov.info'), { force: true });
}

async function snapshot() {
    const assets = resolve(root, 'dist/assets');
    await mkdir(buildDir, { recursive: true });
    for (const name of (await readdir(assets)).filter((name) => name.endsWith('.js'))) {
        await copyFile(resolve(assets, name), resolve(buildDir, name));
        await copyFile(resolve(assets, `${name}.map`), resolve(buildDir, `${name}.map`));
    }
}

async function merge() {
    const unit = createCoverageMap(JSON.parse(await readFile(resolve(coverageDir, 'coverage-final.json'), 'utf8')));
    const browser = createCoverageMap({});
    const files = (await readdir(browserDir)).filter((name) => name.endsWith('.json'));
    if (files.length === 0) throw new Error('No Playwright coverage files were collected.');
    let mappedScripts = 0;
    const coveredBundles = new Set();
    const verifiedBundles = new Set();

    for (const file of files) {
        const entries = JSON.parse(await readFile(resolve(browserDir, file), 'utf8'));
        for (const entry of entries) {
            const pathname = new URL(entry.url).pathname;
            if (!/^\/assets\/[^/]+\.js$/.test(pathname)) continue;
            const name = basename(pathname);
            coveredBundles.add(name);
            const scriptPath = resolve(buildDir, name);
            const code = await readFile(scriptPath, 'utf8');
            if (entry.source !== undefined) {
                if (code !== entry.source) throw new Error(`Browser code differs from ${scriptPath}.`);
                verifiedBundles.add(name);
            }
            const sourceMap = JSON.parse(await readFile(`${scriptPath}.map`, 'utf8'));
            const runtimePath = resolve(root, 'dist/assets', basename(pathname));
            let containsAppSource = false;
            for (const [index, source] of sourceMap.sources.entries()) {
                const path = resolve(dirname(runtimePath), source);
                if (!appSource(path)) continue;
                containsAppSource = true;
                if (sourceMap.sourcesContent?.[index] !== (await readFile(path, 'utf8'))) {
                    throw new Error(`Source map differs from current app source: ${path}.`);
                }
            }
            if (!containsAppSource) continue;
            const mapped = await convert({
                code,
                sourceMap,
                coverage: { url: pathToFileURL(runtimePath).href, functions: entry.functions },
                ast: parseAstAsync(code),
            });
            const appFiles = Object.fromEntries(Object.entries(mapped).filter(([path]) => appSource(path)));
            if (Object.keys(appFiles).length === 0) throw new Error(`No app source mapped from ${scriptPath}.`);
            browser.merge(appFiles);
            mappedScripts += 1;
        }
    }

    for (const name of coveredBundles) {
        if (!verifiedBundles.has(name)) throw new Error(`No browser source verified for ${name}.`);
    }
    if (mappedScripts === 0) throw new Error('No built app scripts were covered by Playwright.');
    const gameplayPath = resolve(sourceDir, 'screens/Gameplay.tsx');
    if (!browser.files().includes(gameplayPath)) throw new Error('Gameplay.tsx did not map from browser coverage.');
    const gameplayLines = browser.fileCoverageFor(gameplayPath).getLineCoverage();
    if (!Object.values(gameplayLines).some((hits) => hits > 0)) {
        throw new Error('Playwright did not cover a Gameplay.tsx source line.');
    }

    unit.merge(browser);
    create('lcovonly', { file: 'lcov.info', projectRoot: root }).execute(
        createContext({ dir: coverageDir, coverageMap: unit }),
    );
    console.log(`Merged ${String(mappedScripts)} browser scripts from ${String(files.length)} real-backend journeys.`);
}

const command = process.argv[2];
if (command === 'prepare') await prepare();
else if (command === 'snapshot') await snapshot();
else if (command === 'merge') await merge();
else throw new Error('Usage: node scripts/browser-coverage.mjs prepare|snapshot|merge');
