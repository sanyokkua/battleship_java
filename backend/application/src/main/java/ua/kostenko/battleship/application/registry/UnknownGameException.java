package ua.kostenko.battleship.application.registry;

/** The game id names no slot in the registry: it never existed, or has been reclaimed. */
public final class UnknownGameException extends IllegalArgumentException {
    public UnknownGameException() {
        super("unknown game id");
    }
}
