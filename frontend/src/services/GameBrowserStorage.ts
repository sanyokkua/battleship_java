import {ResponseCreatedPlayerDto} from "../logic/ApplicationTypes";

const PLAYER = "player_obj";
const SESSION = "session_str";
const GAME_STAGE = "gameStage_str";

export function saveSession(sessionId: string): void {
    if (!sessionId || sessionId.trim().length === 0) {
        throw new Error("Session is not valid. Can't be saved to browser.");
    }
    localStorage.setItem(SESSION, sessionId);
}

export function loadSession(): string {
    const item = localStorage.getItem(SESSION);
    return item ? item : "";
}

export function savePlayer(playerDto: ResponseCreatedPlayerDto): void {
    if (!playerDto) {
        throw new Error("Player is not valid. Can't be saved to browser.");
    }
    localStorage.setItem(PLAYER, JSON.stringify(playerDto));
}

export function loadPlayer(): ResponseCreatedPlayerDto | null {
    const loadedDto = localStorage.getItem(PLAYER);
    if (loadedDto) {
        return JSON.parse(loadedDto);
    }
    return null;
}

export function saveStage(gameStage: string): void {
    if (!gameStage || gameStage.trim().length === 0) {
        throw new Error("Game Stage is not valid. Can't be saved to browser.");
    }
    localStorage.setItem(GAME_STAGE, gameStage);
}

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