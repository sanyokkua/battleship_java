import {ResponseCreatedPlayerDto} from "../logic/ApplicationTypes";

const PLAYER = "player_obj";
const SESSION = "session_str";
const GAME_STAGE = "gameStage_str";

/**
 * Persists the current session id to localStorage so it survives page reloads.
 *
 * @throws Error if sessionId is empty/blank.
 */
export function saveSession(sessionId: string): void {
    if (!sessionId || sessionId.trim().length === 0) {
        throw new Error("Session is not valid. Can't be saved to browser.");
    }
    localStorage.setItem(SESSION, sessionId);
}

/**
 * Reads the persisted session id, or "" if none has been saved.
 */
export function loadSession(): string {
    const item = localStorage.getItem(SESSION);
    return item ? item : "";
}

/**
 * Persists the current player (serialized as JSON) to localStorage.
 *
 * @throws Error if playerDto is null/undefined.
 */
export function savePlayer(playerDto: ResponseCreatedPlayerDto): void {
    if (!playerDto) {
        throw new Error("Player is not valid. Can't be saved to browser.");
    }
    localStorage.setItem(PLAYER, JSON.stringify(playerDto));
}

/**
 * Reads and JSON-parses the persisted player, or null if none has been saved.
 */
export function loadPlayer(): ResponseCreatedPlayerDto | null {
    const loadedDto = localStorage.getItem(PLAYER);
    if (loadedDto) {
        return JSON.parse(loadedDto);
    }
    return null;
}

/**
 * Persists the current GameStage (as a string) to localStorage, used by StageGuard on
 * reload to restore routing without waiting for a fresh backend poll.
 *
 * @throws Error if gameStage is empty/blank.
 */
export function saveStage(gameStage: string): void {
    if (!gameStage || gameStage.trim().length === 0) {
        throw new Error("Game Stage is not valid. Can't be saved to browser.");
    }
    localStorage.setItem(GAME_STAGE, gameStage);
}

/**
 * Reads the persisted GameStage, or null if none has been saved.
 */
export function loadStage(): string | null {
    const item = localStorage.getItem(GAME_STAGE);
    return item ? item : null;
}

/**
 * Clears all persisted session/player/stage data. Used when the player
 * leaves an in-progress game (e.g. via the AppBar's "Leave this game?"
 * confirmation) or otherwise returns to the menu.
 */
export function clearGameData(): void {
    localStorage.removeItem(SESSION);
    localStorage.removeItem(PLAYER);
    localStorage.removeItem(GAME_STAGE);
}