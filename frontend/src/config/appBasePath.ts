const RESERVED_TERMINAL_PATHS = [
    "/join",
    "/game/wait",
    "/game/preparation",
    "/game/gameplay",
    "/game/results",
] as const;

export class AppBasePathConfigError extends Error {
    readonly code = "INVALID_APP_BASE_PATH" as const;

    constructor(raw: string | undefined, reason: string) {
        super(`Invalid application base path "${raw ?? ""}": ${reason}`);
        this.name = "AppBasePathConfigError";
        Object.setPrototypeOf(this, new.target.prototype);
    }
}

export function normalizeAppBasePath(raw: string | undefined): string {
    const value = raw ?? "";

    if (value === "" || /^\/+$/u.test(value)) {
        return "/";
    }

    if (value.includes("?") || value.includes("#")) {
        throw new AppBasePathConfigError(raw, "query and hash fragments are not allowed");
    }

    const normalized = `/${value.replace(/^\/+|\/+$/gu, "")}`;

    if (RESERVED_TERMINAL_PATHS.some((suffix) => normalized.endsWith(suffix))) {
        throw new AppBasePathConfigError(raw, "the path ends with a reserved application route");
    }

    return normalized === "/" ? "/" : normalized;
}

export function toViteAssetBasePath(normalizedBasePath: string): string {
    return normalizedBasePath === "/" ? "/" : `${normalizedBasePath}/`;
}

export function buildCanonicalJoinUrl(
    origin: string,
    normalizedBasePath: string,
    sessionId: string,
): string | null {
    if (!sessionId.trim()) {
        return null;
    }

    const joinPath = normalizedBasePath === "/" ? "/join" : `${normalizedBasePath}/join`;
    const url = new URL(joinPath, origin);
    url.searchParams.set("id", sessionId);
    return url.toString();
}
