package ua.kostenko.battleship.app.registry;

public final class CapacityExceededException extends RuntimeException {
    private final int retryAfterSeconds;

    public CapacityExceededException(String resource, int retryAfterSeconds) {
        super(resource + " capacity exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
