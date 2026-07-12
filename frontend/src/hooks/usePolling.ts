import {useEffect, useLayoutEffect, useRef} from "react";

/**
 * StrictMode-safe interval hook.
 *
 * Semantics (documented, chosen deliberately):
 * - `fn` fires immediately on mount (and again any time `intervalMs`/`enabled`
 *   change causing the effect to re-run), then every `intervalMs` after that.
 *   Firing immediately gives a good UX — screens show data right away instead
 *   of a blank state for the first `intervalMs`.
 * - The effect is keyed on `[intervalMs, enabled]` only, NOT on `fn`. `fn` is
 *   typically a fresh closure every render (e.g. an inline arrow function), so
 *   depending on it would tear down and rebuild the interval every render,
 *   which both defeats the purpose of a stable interval and risks missed/extra
 *   ticks. Instead we stash the latest `fn` in a ref and always call through
 *   the ref, so the interval callback never uses a stale closure.
 * - Under React 19 StrictMode, effects are mounted, cleaned up, and
 *   re-mounted once in dev. Because setup/cleanup are symmetric here
 *   (setInterval in the effect body, clearInterval in its cleanup), the first
 *   (discarded) mount's interval is cleared before the second mount's interval
 *   is created, so there is never more than one live interval at a time and no
 *   duplicate/leaked polling.
 */
export function usePolling(fn: () => void | Promise<void>, intervalMs: number, enabled: boolean): void {
    const fnRef = useRef(fn);
    // Refs must not be written during render (react-hooks/refs) — update it in a layout
    // effect instead, which still runs before the browser paints/before any interval tick
    // could observe a stale value.
    useLayoutEffect(() => {
        fnRef.current = fn;
    });

    useEffect(() => {
        if (!enabled) {
            return;
        }

        fnRef.current();
        const id = setInterval(() => {
            fnRef.current();
        }, intervalMs);

        return () => {
            clearInterval(id);
        };
    }, [intervalMs, enabled]);
}
