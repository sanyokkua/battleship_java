export function readCsrfToken(): string | undefined {
    const cookie = document.cookie.split('; ').find((entry) => entry.startsWith('XSRF-TOKEN='));
    return cookie === undefined ? undefined : decodeURIComponent(cookie.slice('XSRF-TOKEN='.length));
}
