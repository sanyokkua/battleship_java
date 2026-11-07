import { useEffect, useState } from 'react';

export function useRetryWindow(retryAtMs: number | undefined): boolean {
    const [now, setNow] = useState(Date.now);
    useEffect(() => {
        if (retryAtMs === undefined) return;
        const remaining = retryAtMs - Date.now();
        const timer = globalThis.setTimeout(
            () => {
                setNow(Date.now());
            },
            Math.max(0, remaining),
        );
        return () => {
            globalThis.clearTimeout(timer);
        };
    }, [retryAtMs]);
    return retryAtMs !== undefined && retryAtMs > now;
}
