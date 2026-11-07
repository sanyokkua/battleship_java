package ua.kostenko.battleship.application.registry;

public final class BrowserGameLimitException extends RuntimeException {
    public BrowserGameLimitException() {
        super("browser live-game limit reached");
    }
}
