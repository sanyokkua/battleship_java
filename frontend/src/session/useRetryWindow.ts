import { useEffect, useState } from 'react';

export function useRetryWindow(retryAtMs: number | undefined): boolean {
    const [now, setNow] = useState(Date.now);
    useEffect(() => {
        if (retryAtMs === undefined) return;
        const remaining = retryAtMs - Date.now();
        const timer = window.setTimeout(
            () => {
                setNow(Date.now());
            },
            Math.max(0, remaining),
        );
        return () => {
            window.clearTimeout(timer);
        };
    }, [retryAtMs]);
    return retryAtMs !== undefined && retryAtMs > now;
}
