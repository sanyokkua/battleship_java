import {GameStage, PlayerDto} from "../logic/GameTypes";

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

export function savePlayer(playerDto: PlayerDto): void {
    if (!playerDto) {
        throw new Error("Player is not valid. Can't be saved to browser.");
    }
    localStorage.setItem(PLAYER, JSON.stringify(playerDto));
}

export function loadPlayer(): PlayerDto | null {
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

export function loadStage(): GameStage | null {
    const item: GameStage = localStorage.getItem(GAME_STAGE) as GameStage;
    return item ? item : null;
}