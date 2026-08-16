import {
    AppBasePathConfigError,
    buildCanonicalJoinUrl,
    normalizeAppBasePath,
    toViteAssetBasePath,
} from "./appBasePath";
import {APP_BASE_PATH} from "./appConfig";

describe("browser runtime base path", () => {
    it("exports the normalized Vite runtime base path", () => {
        expect(APP_BASE_PATH).toBe(normalizeAppBasePath(import.meta.env.BASE_URL));
        expect(toViteAssetBasePath(APP_BASE_PATH)).toBe(import.meta.env.BASE_URL);
    });
});

describe("normalizeAppBasePath", () => {
    it.each([
        [undefined, "/"],
        ["", "/"],
        ["/", "/"],
        ["///", "/"],
        ["battleship", "/battleship"],
        ["/battleship/", "/battleship"],
        ["//battleship///", "/battleship"],
        ["/battleship/v1/", "/battleship/v1"],
        ["/battleship//v1///", "/battleship//v1"],
        ["/battleship/game/wait-assets", "/battleship/game/wait-assets"],
        ["/community/join-us/", "/community/join-us"],
        ["/battleship/v1", "/battleship/v1"],
    ])("normalizes %j to %j", (raw, expected) => {
        expect(normalizeAppBasePath(raw)).toBe(expected);
    });

    it.each([
        "/join",
        "/battleship/join",
        "/game/wait",
        "/battleship/game/wait/",
        "/game/preparation",
        "/battleship/game/gameplay///",
        "/game/results",
        "/battleship/game/results",
        "/battleship/?preview=1",
        "/battleship/#preview",
    ])("rejects invalid terminal/configuration path %j", (raw) => {
        expect(() => normalizeAppBasePath(raw)).toThrow(AppBasePathConfigError);

        try {
            normalizeAppBasePath(raw);
        } catch (error) {
            expect(error).toBeInstanceOf(AppBasePathConfigError);
            expect(error).toMatchObject({code: "INVALID_APP_BASE_PATH"});
            expect((error as Error).message).toContain(raw);
        }
    });

    it("exposes a typed error code for invalid application base paths", () => {
        expect(() => normalizeAppBasePath("/join")).toThrowError(
            expect.objectContaining({code: "INVALID_APP_BASE_PATH"}),
        );
    });
});

describe("toViteAssetBasePath", () => {
    it.each([
        ["/", "/"],
        ["/battleship", "/battleship/"],
        ["/battleship/v1", "/battleship/v1/"],
    ])("converts logical base %j to asset base %j", (logical, expected) => {
        expect(toViteAssetBasePath(logical)).toBe(expected);
    });
});

describe("buildCanonicalJoinUrl", () => {
    it.each([
        ["http://192.168.9.1:8080", "/", "session-1", "http://192.168.9.1:8080/join?id=session-1"],
        ["https://battleship.example.com", "/battleship", "session-2", "https://battleship.example.com/battleship/join?id=session-2"],
        ["http://localhost:5173", "/battleship/v1", "session-3", "http://localhost:5173/battleship/v1/join?id=session-3"],
    ])("builds the canonical URL for %s and %s", (origin, basePath, sessionId, expected) => {
        expect(buildCanonicalJoinUrl(origin, basePath, sessionId)).toBe(expected);
    });

    it.each(["", "   ", "\t\n"]) (
        "returns null for a blank session ID %j",
        (sessionId) => {
            expect(buildCanonicalJoinUrl("https://battleship.example.com", "/", sessionId)).toBeNull();
        },
    );

    it("uses URLSearchParams serialization and round-trips the original ID", () => {
        const sessionId = "space & plus+slash/percent% unicode Ž";
        const url = buildCanonicalJoinUrl("https://battleship.example.com", "/battleship", sessionId);

        expect(url).toBe(
            "https://battleship.example.com/battleship/join?id=space+%26+plus%2Bslash%2Fpercent%25+unicode+%C5%BD",
        );
        expect(new URL(url!).searchParams.get("id")).toBe(sessionId);
    });

    it.each([
        ["http://192.168.9.1:8080", "/", "http://192.168.9.1:8080/join?id=session"],
        ["https://battleship.example.com", "/battleship", "https://battleship.example.com/battleship/join?id=session"],
        ["https://battleship.example.com:9443", "/battleship", "https://battleship.example.com:9443/battleship/join?id=session"],
    ])("preserves origin details and includes the logical base path once", (origin, basePath, expected) => {
        const url = buildCanonicalJoinUrl(origin, basePath, "session");

        expect(url).toBe(expected);
        expect(new URL(url!).pathname.split(basePath === "/" ? "/join" : basePath).length - 1).toBe(1);
    });
});
