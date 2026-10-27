export interface StorageGateway {
    read(key: string): { ok: true; value: string | null } | { ok: false; error: unknown };
    write(key: string, value: string): { ok: true; value: null } | { ok: false; error: unknown };
    remove(key: string): { ok: true; value: null } | { ok: false; error: unknown };
}

export function createStorageGateway(storage?: Storage | null): StorageGateway {
    let target = storage;
    if (target === undefined) {
        try {
            target = typeof window === 'undefined' ? null : window.localStorage;
        } catch {
            target = null;
        }
    }
    const run = <T>(operation: (storage: Storage) => T) => {
        try {
            if (target == null) throw new Error('Storage unavailable');
            return { ok: true as const, value: operation(target) };
        } catch (error) {
            return { ok: false as const, error };
        }
    };
    return {
        read: (key) => run((value) => value.getItem(key)),
        write: (key, value) =>
            run((storageValue) => {
                storageValue.setItem(key, value);
                return null;
            }),
        remove: (key) =>
            run((value) => {
                value.removeItem(key);
                return null;
            }),
    };
}
