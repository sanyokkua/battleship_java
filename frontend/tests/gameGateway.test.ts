import { afterEach, expect, it, vi } from 'vitest';

afterEach(() => {
    vi.unstubAllGlobals();
    vi.resetModules();
});

const json = (body: unknown, status: number, type = 'application/json') =>
    new Response(JSON.stringify(body), { status, headers: { 'content-type': type } });

// R20: the refresh after a 409 is best effort; its failure must not replace the definite refusal.
it('rethrows the original 409 when the refresh read fails', async () => {
    const fetchStub = vi.fn((input: Request | string) => {
        const request = typeof input === 'string' ? new Request(input) : input;
        const { pathname } = new URL(request.url, 'http://localhost');
        if (pathname === '/api/v1/meta') {
            return Promise.resolve(json({ apiVersion: 'v1', serverTime: '2026-09-20T12:00:00.000Z', limits: {} }, 200));
        }
        if (request.method === 'POST') {
            return Promise.resolve(
                json({ title: 'Conflict', status: 409, code: 'action-not-allowed' }, 409, 'application/problem+json'),
            );
        }
        return Promise.resolve(
            new Response('<html>Bad gateway</html>', { status: 502, headers: { 'content-type': 'text/html' } }),
        );
    });
    vi.stubGlobal('fetch', fetchStub);
    // The client uses same-origin relative URLs; Node's Request needs them resolved against the page.
    const NativeRequest = Request;
    vi.stubGlobal(
        'Request',
        class extends NativeRequest {
            constructor(input: RequestInfo | URL, init?: RequestInit) {
                super(typeof input === 'string' ? new URL(input, globalThis.location.href).href : input, init);
            }
        },
    );
    const { GatewayError, sendCommand } = await import('../src/gateway/gameGateway');

    const failure = await sendCommand('Game0000000000000001', {
        commandId: '5f2b1c7e-8a3a-4c1e-9d5b-0a1b2c3d4e5f',
        command: { type: 'READY' },
    }).catch((error: unknown) => error);

    expect(failure).toBeInstanceOf(GatewayError);
    expect((failure as InstanceType<typeof GatewayError>).problem.status).toBe(409);
});
