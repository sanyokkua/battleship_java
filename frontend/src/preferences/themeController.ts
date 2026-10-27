import { preferenceStore } from './preferences';

export interface ThemeController {
    dispose(): void;
}

export function createThemeController(
    store: Pick<typeof preferenceStore, 'get' | 'subscribe'>,
    root: HTMLElement,
    matchMedia: ((query: string) => MediaQueryList) | null = typeof window.matchMedia === 'function'
        ? window.matchMedia.bind(window)
        : null,
): ThemeController {
    const media = matchMedia === null ? null : matchMedia('(prefers-color-scheme: dark)');
    const apply = () => {
        const preference = store.get().theme;
        root.dataset.theme = preference === 'system' ? (media?.matches ? 'dark' : 'light') : preference;
    };
    apply();
    const unsubscribe = store.subscribe(apply);
    if (media !== null) media.addEventListener('change', apply);
    let disposed = false;
    return {
        dispose() {
            if (disposed) return;
            disposed = true;
            unsubscribe();
            if (media !== null) media.removeEventListener('change', apply);
        },
    };
}
