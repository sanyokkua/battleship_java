import {expect, test, type Page} from '@playwright/test';
import {readPersistedSession} from './support/mockBackdoor';

function logicalBasePath(): string {
    const raw = process.env.VITE_APP_BASE_PATH ?? '/';
    const trimmed = raw.replace(/^\/+|\/+$/g, '');
    return trimmed ? `/${trimmed}` : '/';
}

function routePath(basePath: string, path: string): string {
    return basePath === '/' ? path : `${basePath}${path}`;
}

async function createSessionThroughUi(page: Page): Promise<string> {
    const basePath = logicalBasePath();
    await page.goto(routePath(basePath, '/'));
    await page.getByRole('button', {name: 'New Game'}).click();
    await page.getByRole('radio', {name: /Ukrainian/i}).click();
    await page.getByLabel('Player name').fill('Alice');
    await page.getByRole('button', {name: 'Start new game'}).click();
    await expect(page).toHaveURL(new RegExp(`${basePath === '/' ? '' : basePath.replace(/\//g, '\\/')}/game/wait$`));

    const {sessionId, player} = await readPersistedSession(page);
    expect(sessionId).toBeTruthy();
    expect(player).not.toBeNull();
    return sessionId;
}

test.describe('QR session sharing mock-browser journey', () => {
    test('creates a waiting session, opens QR on demand, and resolves the same join route', async ({page}) => {
        test.setTimeout(45000);
        const basePath = logicalBasePath();
        const assetBasePath = basePath === '/' ? '/' : `${basePath}/`;
        const sessionId = await createSessionThroughUi(page);

        const assetUrls = await page.locator('script[src], link[rel="stylesheet"][href]').evaluateAll((elements) =>
            elements.map((element) =>
                element instanceof HTMLScriptElement ? element.src : (element as HTMLLinkElement).href,
            ),
        );
        expect(assetUrls.length).toBeGreaterThan(0);
        expect(assetUrls.every((assetUrl) => new URL(assetUrl).pathname.startsWith(assetBasePath))).toBe(true);

        await expect(page.getByRole('dialog')).toHaveCount(0);
        await expect(page.locator('canvas')).toHaveCount(0);

        const showQrButton = page.getByRole('button', {name: 'Show QR code'});
        await showQrButton.focus();
        await showQrButton.press('Enter');
        const dialog = page.getByRole('dialog');
        await expect(dialog).toBeVisible();
        await expect(dialog.getByRole('img', {name: /QR code|QR-код/i})).toBeVisible();
        await expect(dialog.locator('canvas')).toHaveCount(1);
        await expect(dialog.getByRole('heading', {name: 'Join with QR code'})).toBeVisible();
        await expect(dialog.getByText(/Scan this code|Скануйте цей код/i)).toBeVisible();
        await expect(dialog.getByRole('button', {name: 'Close'})).toBeVisible();

        await dialog.getByRole('button', {name: 'Close'}).click();
        await expect(dialog).toHaveCount(0);
        await expect(showQrButton).toBeFocused();

        await showQrButton.focus();
        await showQrButton.press('Enter');
        await expect(dialog.getByRole('img', {name: /QR code|QR-код/i})).toBeVisible();
        await page.keyboard.press('Escape');
        await expect(dialog).toHaveCount(0);
        await expect(showQrButton).toBeFocused();

        await showQrButton.focus();
        await showQrButton.press('Enter');
        await expect(dialog.getByRole('img', {name: /QR code|QR-код/i})).toBeVisible();
        await page.locator('.sheet-backdrop').click({position: {x: 5, y: 5}});
        await expect(dialog).toHaveCount(0);
        await expect(showQrButton).toBeFocused();

        await showQrButton.focus();
        await showQrButton.press('Enter');
        await expect(dialog.getByRole('img', {name: /QR code|QR-код/i})).toBeVisible();
        await dialog.getByRole('button', {name: 'Close'}).click();
        await expect(dialog).toHaveCount(0);
        await expect(showQrButton).toBeFocused();

        await page.getByRole('button', {name: 'УКР'}).click();
        const ukrainianShowQrButton = page.getByRole('button', {name: 'Показати QR-код'});
        await ukrainianShowQrButton.focus();
        await ukrainianShowQrButton.press('Enter');
        const ukrainianDialog = page.getByRole('dialog');
        await expect(ukrainianDialog.getByRole('img', {name: /QR-код/})).toBeVisible();
        await expect(ukrainianDialog.getByRole('heading', {name: 'Приєднатися за QR-кодом'})).toBeVisible();
        await expect(ukrainianDialog.getByText('Скануйте цей код телефоном, щоб приєднатися до гри.')).toBeVisible();
        await expect(ukrainianDialog.getByRole('button', {name: 'Закрити'})).toBeVisible();
        await ukrainianDialog.getByRole('button', {name: 'Закрити'}).click();
        await expect(ukrainianDialog).toHaveCount(0);
        await expect(ukrainianShowQrButton).toBeFocused();

        await page.getByRole('button', {name: 'EN'}).click();
        await page.setViewportSize({width: 320, height: 640});
        const narrowShowQrButton = page.getByRole('button', {name: 'Show QR code'});
        await narrowShowQrButton.focus();
        await narrowShowQrButton.press('Enter');
        const narrowDialog = page.getByRole('dialog');
        await expect(narrowDialog.getByRole('img', {name: /QR code/})).toBeVisible();

        const viewportMetrics = await page.evaluate(() => ({
            bodyScrollWidth: document.body.scrollWidth,
            documentScrollWidth: document.documentElement.scrollWidth,
            innerWidth: window.innerWidth,
        }));
        expect(viewportMetrics.bodyScrollWidth).toBeLessThanOrEqual(viewportMetrics.innerWidth);
        expect(viewportMetrics.documentScrollWidth).toBeLessThanOrEqual(viewportMetrics.innerWidth);

        const sheetBox = await narrowDialog.boundingBox();
        expect(sheetBox).not.toBeNull();
        expect(sheetBox?.x).toBe(0);
        expect(sheetBox?.width).toBe(viewportMetrics.innerWidth);
        const canvasBox = await narrowDialog.locator('canvas').boundingBox();
        expect(canvasBox).not.toBeNull();
        expect(canvasBox?.width).toBeGreaterThan(0);
        expect(canvasBox?.width).toBeLessThanOrEqual(300);
        expect(canvasBox?.height).toBeCloseTo(canvasBox?.width ?? 0, 3);
        await narrowDialog.getByRole('button', {name: 'Close'}).click();
        await expect(narrowDialog).toHaveCount(0);
        await expect(narrowShowQrButton).toBeFocused();

        await page.setViewportSize({width: 1280, height: 720});

        const expectedJoinUrl = new URL(
            routePath(basePath, '/join'),
            new URL(page.url()).origin,
        );
        expectedJoinUrl.searchParams.set('id', sessionId);
        await page.goto(expectedJoinUrl.toString());

        await expect(page.getByLabel('Game ID')).toHaveValue(sessionId);
        await expect(page.getByText('Valid game code')).toHaveCount(0);

        const validUuid = '123e4567-e89b-12d3-a456-426614174000';
        const validJoinUrl = new URL(routePath(basePath, '/join'), new URL(page.url()).origin);
        validJoinUrl.searchParams.set('id', validUuid);
        await page.goto(validJoinUrl.toString());
        await expect(page.getByLabel('Game ID')).toHaveValue(validUuid);
        await expect(page.getByText('Valid game code')).toBeVisible();
        await page.getByLabel('Player name').fill('Bob');
        await expect(page.getByRole('button', {name: 'Join the battle'})).toBeEnabled();
    });
});
