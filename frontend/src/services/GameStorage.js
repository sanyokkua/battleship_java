const PLAYER = "player_obj";
const SESSION = "session_str";

export function saveSession(sessionId) {
    localStorage.setItem(SESSION, sessionId);
}

export function loadSession() {
    const item = localStorage.getItem(SESSION);
    return item ? item : "";
}

export function savePlayer(playerDto) {
    localStorage.setItem(PLAYER, JSON.stringify(playerDto));
}

export function loadPlayer() {
    const loadedDto = localStorage.getItem(PLAYER);
    if (loadedDto) {
        return JSON.parse(loadedDto);
    }
    return null;
}