import { isStackReady } from '../e2e/helpers/stack-ready';

describe('stack readiness', () => {
    it('does not accept backend readiness before the frontend origin is ready', () => {
        expect(isStackReady('[backend] ready at localhost:8084', 'http://localhost:5174')).toBe(false);
    });

    it('accepts the ready announcement for the requested frontend origin', () => {
        expect(isStackReady('[stack] ready at http://localhost:5174', 'http://localhost:5174')).toBe(true);
    });
});
