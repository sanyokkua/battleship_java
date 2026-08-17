import {expect, test, type Page} from '@playwright/test';
import {
    createOpponent,
    hardNavigate,
    persistStage,
    placeFullFleetAndReady,
    readPersistedSession,
} from './support/mockBackdoor';

type AudioStubMode = 'normal' | 'missing-context' | 'rejected-resume' | 'output-failure' | 'node-failure';

type AudioStubSnapshot = {
    contextCreated: number;
    resumeCalls: number;
    scheduledNodes: number;
    scheduleFailures: number;
};

async function installAudioStub(page: Page, mode: AudioStubMode): Promise<void> {
    await page.addInitScript(({mode: initialMode}) => {
        const control = {
            mode: initialMode,
            contextCreated: 0,
            resumeCalls: 0,
            scheduledNodes: 0,
            scheduleFailures: 0,
        };

        Object.defineProperty(window, '__audioTestControl', {
            configurable: true,
            value: control,
        });

        if (initialMode === 'missing-context') {
            Object.defineProperty(window, 'AudioContext', {configurable: true, value: undefined});
            Object.defineProperty(window, 'webkitAudioContext', {configurable: true, value: undefined});
            return;
        }

        function createParam() {
            return {
                value: 0,
                setValueAtTime(value: number) {
                    this.value = value;
                },
                linearRampToValueAtTime(value: number) {
                    this.value = value;
                },
                exponentialRampToValueAtTime(value: number) {
                    this.value = value;
                },
            };
        }

        function createNode() {
            return {
                connect() {
                    if (control.mode === 'output-failure') {
                        throw new Error('output unavailable');
                    }
                },
                disconnect() {
                    // Best-effort cleanup is intentionally a no-op in the stub.
                },
            };
        }

        class TestAudioContext {
            state: 'suspended' | 'running' | 'closed' = 'suspended';
            currentTime = 0;
            sampleRate = 44100;

            constructor() {
                control.contextCreated += 1;
            }

            get destination() {
                return {};
            }

            resume() {
                control.resumeCalls += 1;
                if (control.mode === 'rejected-resume') {
                    return Promise.reject(new Error('resume rejected'));
                }
                this.state = 'running';
                return Promise.resolve();
            }

            close() {
                this.state = 'closed';
                return Promise.resolve();
            }

            createGain() {
                return {...createNode(), gain: createParam()};
            }

            createOscillator() {
                if (control.mode === 'node-failure') {
                    control.scheduleFailures += 1;
                    throw new Error('oscillator unavailable');
                }
                return {
                    ...createNode(),
                    type: 'sine',
                    frequency: createParam(),
                    start() {
                        control.scheduledNodes += 1;
                    },
                    stop() {
                        // The service owns cleanup; the stub does not need to fire onended.
                    },
                    onended: null,
                };
            }

            createBufferSource() {
                if (control.mode === 'node-failure') {
                    control.scheduleFailures += 1;
                    throw new Error('buffer source unavailable');
                }
                return {
                    ...createNode(),
                    buffer: null,
                    start() {
                        control.scheduledNodes += 1;
                    },
                    stop() {
                        // The service owns cleanup; the stub does not need to fire onended.
                    },
                    onended: null,
                };
            }

            createBuffer(_channels: number, length: number, sampleRate: number) {
                const data = new Float32Array(length);
                return {
                    duration: length / sampleRate,
                    getChannelData() {
                        return data;
                    },
                };
            }

            createBiquadFilter() {
                return {...createNode(), type: 'lowpass', frequency: createParam()};
            }
        }

        Object.defineProperty(window, 'AudioContext', {
            configurable: true,
            value: TestAudioContext,
        });
        Object.defineProperty(window, 'webkitAudioContext', {
            configurable: true,
            value: TestAudioContext,
        });
    }, {mode});
}

async function readAudioStub(page: Page): Promise<AudioStubSnapshot> {
    return page.evaluate(() => {
        const control = (window as Window & {
            __audioTestControl?: AudioStubSnapshot;
        }).__audioTestControl;
        if (!control) throw new Error('audio test control is missing');
        return {...control};
    });
}

async function reachGameplay(page: Page): Promise<void> {
    await page.goto('/');
    await page.getByRole('button', {name: 'New Game'}).click();
    await page.getByRole('radio', {name: /Milton Bradley/i}).click();
    await page.getByLabel('Player name').fill('Audio tester');
    await page.getByRole('button', {name: 'Start new game'}).click();

    const {sessionId, player} = await readPersistedSession(page);
    expect(player).not.toBeNull();
    const opponent = await createOpponent(page, sessionId, 'Audio opponent');
    await placeFullFleetAndReady(page, sessionId, player!.playerId);
    await placeFullFleetAndReady(page, sessionId, opponent.playerId);

    await persistStage(page, 'IN_GAME');
    await hardNavigate(page, '/game/gameplay');
    await expect(page.locator('.scoreboard')).toBeVisible();
}

async function assertNoPageErrors(errors: Error[]): Promise<void> {
    expect(errors, errors.map(error => error.message).join('\n')).toEqual([]);
}

test('gameplay sound control survives normal interaction, locale changes, and results navigation', async ({page}) => {
    test.setTimeout(90000);
    const errors: Error[] = [];
    page.on('pageerror', error => errors.push(error));
    await installAudioStub(page, 'normal');
    await reachGameplay(page);

    await expect(page.getByRole('button', {name: 'Sound on'})).toHaveAttribute('aria-pressed', 'true');
    const beforeKeyboardGesture = await readAudioStub(page);
    const soundToggle = page.getByRole('button', {name: 'Sound on'});
    await soundToggle.focus();
    await page.keyboard.press('Space');
    await expect(page.getByRole('button', {name: 'Sound off'})).toHaveAttribute('aria-pressed', 'false');
    const afterKeyboardGesture = await readAudioStub(page);
    expect(afterKeyboardGesture.resumeCalls).toBeGreaterThanOrEqual(beforeKeyboardGesture.resumeCalls);

    await page.getByRole('button', {name: 'Sound off'}).click();
    await expect(page.getByRole('button', {name: 'Sound on'})).toHaveAttribute('aria-pressed', 'true');

    const targetBoard = page.locator('.bp-target .board');
    await targetBoard.getByRole('button').first().click();
    await expect.poll(async () => (await readAudioStub(page)).scheduledNodes).toBeGreaterThan(0);

    await page.getByRole('button', {name: 'Sound on'}).click();
    await expect(page.getByRole('button', {name: 'Sound off'})).toHaveAttribute('aria-pressed', 'false');
    await page.getByRole('button', {name: 'УКР'}).click();
    await expect(page.getByRole('button', {name: 'Звук вимкнено'})).toHaveAttribute('aria-pressed', 'false');
    await page.getByRole('button', {name: 'EN'}).click();
    await expect(page.getByRole('button', {name: 'Sound off'})).toHaveAttribute('aria-pressed', 'false');

    const beforeRootGesture = await readAudioStub(page);
    await hardNavigate(page, '/');
    await expect(page.getByRole('button', {name: 'New Game'})).toBeVisible();
    await expect(page.locator('.gameplay-screen')).toHaveCount(0);
    await expect(page.getByRole('button', {name: /Sound/})).toHaveCount(0);
    await page.mouse.click(8, 8);
    await page.keyboard.press('Enter');
    await expect.poll(async () => (await readAudioStub(page)).scheduledNodes).toBe(beforeRootGesture.scheduledNodes);
    await expect(page.getByText(/Sound (on|off)/i)).toHaveCount(0);

    await persistStage(page, 'FINISHED');
    await hardNavigate(page, '/game/results');
    await expect(page.getByRole('button', {name: 'Return to main menu'})).toBeVisible();
    await assertNoPageErrors(errors);
});

for (const mode of ['missing-context', 'rejected-resume', 'output-failure', 'node-failure'] as const) {
    test(`gameplay remains usable when audio is in ${mode} mode`, async ({page}) => {
        test.setTimeout(90000);
        const errors: Error[] = [];
        page.on('pageerror', error => errors.push(error));
        await installAudioStub(page, mode);
        await reachGameplay(page);

        await expect(page.getByRole('button', {name: 'Sound on'})).toBeVisible();
        await page.getByRole('button', {name: 'Sound on'}).click();
        await expect(page.getByRole('button', {name: 'Sound off'})).toHaveAttribute('aria-pressed', 'false');

        const targetBoard = page.locator('.bp-target .board');
        await targetBoard.getByRole('button').first().click();
        await expect(targetBoard).toBeVisible();
        expect((await readAudioStub(page)).scheduledNodes).toBe(0);

        await persistStage(page, 'FINISHED');
        await hardNavigate(page, '/game/results');
        await expect(page.getByRole('button', {name: 'Return to main menu'})).toBeVisible();
        await assertNoPageErrors(errors);
    });
}
