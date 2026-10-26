import { redact } from '../../scripts/stack.mjs';

describe('redact', () => {
    it('removes cookie values from process output', () => {
        const line = 'Set-Cookie: __Host-battleship_session=secret; Path=/ Cookie: XSRF-TOKEN=csrf-value';

        expect(redact(line)).not.toContain('secret');
        expect(redact(line)).not.toContain('csrf-value');
    });

    it('removes invitation fragments from process output', () => {
        expect(redact('GET /join/abc#invite=super-secret')).not.toContain('super-secret');
    });

    it('removes XSRF token assignments from process output', () => {
        expect(redact('XSRF-TOKEN=csrf-value')).not.toContain('csrf-value');
    });
});
