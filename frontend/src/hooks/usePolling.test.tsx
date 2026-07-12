import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {render} from "@testing-library/react";
import {StrictMode} from "react";
import {usePolling} from "./usePolling";

/**
 * Testing approach: fake timers (`vi.useFakeTimers()`) + `@testing-library/react`'s
 * `render` (rather than `renderHook`) so we can exercise the hook inside a real
 * component tree, including StrictMode's dev-only double-invoke of effects.
 *
 * Documented semantics under test (see usePolling.ts):
 * - `fn` fires immediately on mount.
 * - `fn` fires again every `intervalMs` after that.
 * - stops firing once `enabled` becomes false.
 * - cleans up on unmount (no further calls even if timers advance).
 * - no duplicate/leaked interval under StrictMode's double-effect-invoke.
 */

function Harness({fn, intervalMs, enabled}: { fn: () => void; intervalMs: number; enabled: boolean }) {
    usePolling(fn, intervalMs, enabled);
    return null;
}

describe("usePolling", () => {
    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it("fires immediately on mount", () => {
        const fn = vi.fn();
        render(<Harness fn={fn} intervalMs={1000} enabled={true}/>);
        expect(fn).toHaveBeenCalledTimes(1);
    });

    it("fires again after intervalMs elapses, repeatedly", () => {
        const fn = vi.fn();
        render(<Harness fn={fn} intervalMs={1000} enabled={true}/>);
        expect(fn).toHaveBeenCalledTimes(1);

        vi.advanceTimersByTime(1000);
        expect(fn).toHaveBeenCalledTimes(2);

        vi.advanceTimersByTime(2000);
        expect(fn).toHaveBeenCalledTimes(4);
    });

    it("always calls the latest fn reference without restarting the interval", () => {
        const calls: string[] = [];
        const fnA = () => calls.push("A");
        const fnB = () => calls.push("B");

        const {rerender} = render(<Harness fn={fnA} intervalMs={1000} enabled={true}/>);
        expect(calls).toEqual(["A"]);

        // Re-render with a new fn reference but same intervalMs/enabled — must NOT
        // reset the interval (i.e. must not fire immediately again on this render).
        rerender(<Harness fn={fnB} intervalMs={1000} enabled={true}/>);
        expect(calls).toEqual(["A"]);

        vi.advanceTimersByTime(1000);
        // The single, still-running interval now calls the latest fn (B), not the stale A.
        expect(calls).toEqual(["A", "B"]);
    });

    it("does nothing when enabled is false", () => {
        const fn = vi.fn();
        render(<Harness fn={fn} intervalMs={1000} enabled={false}/>);
        expect(fn).toHaveBeenCalledTimes(0);

        vi.advanceTimersByTime(5000);
        expect(fn).toHaveBeenCalledTimes(0);
    });

    it("stops firing when enabled transitions from true to false", () => {
        const fn = vi.fn();
        const {rerender} = render(<Harness fn={fn} intervalMs={1000} enabled={true}/>);
        expect(fn).toHaveBeenCalledTimes(1);

        rerender(<Harness fn={fn} intervalMs={1000} enabled={false}/>);

        vi.advanceTimersByTime(5000);
        expect(fn).toHaveBeenCalledTimes(1);
    });

    it("cleans up on unmount — no more calls even if timers advance", () => {
        const fn = vi.fn();
        const {unmount} = render(<Harness fn={fn} intervalMs={1000} enabled={true}/>);
        expect(fn).toHaveBeenCalledTimes(1);

        unmount();

        vi.advanceTimersByTime(5000);
        expect(fn).toHaveBeenCalledTimes(1);
    });

    it("does not leak a duplicate interval under StrictMode's double-effect-invoke", () => {
        const fn = vi.fn();
        render(
            <StrictMode>
                <Harness fn={fn} intervalMs={1000} enabled={true}/>
            </StrictMode>
        );

        // StrictMode dev-mode mounts the effect, cleans it up, and re-mounts it once
        // (mount -> cleanup -> mount, synchronously, before the first paint). Because our
        // effect body itself calls fn() immediately (by design, for the "fire on mount" UX),
        // that immediate call happens once per simulated mount pass — so 2 calls here is
        // expected and is NOT evidence of a leaked interval. What we're actually asserting is
        // that only ONE interval survives: from this point on, each further `intervalMs`
        // tick must add exactly ONE call, not two-or-more (which is what a leaked duplicate
        // interval would produce).
        const callsAfterMount = fn.mock.calls.length;
        expect(callsAfterMount).toBeGreaterThan(0);

        fn.mockClear();
        vi.advanceTimersByTime(1000);
        expect(fn).toHaveBeenCalledTimes(1);

        fn.mockClear();
        vi.advanceTimersByTime(1000);
        expect(fn).toHaveBeenCalledTimes(1);
    });
});
