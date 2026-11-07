package ua.kostenko.battleship.application;

import java.time.Instant;
import ua.kostenko.battleship.application.registry.SessionRegistry;

/** Test setup: a session the registry knows that holds no game, as one whose game has ended. */
public final class SessionFixtures {
    private static final String SETUP_GAME = "session-fixture";

    private SessionFixtures() {}

    public static void register(SessionRegistry sessions, String sessionValue, Instant now) {
        sessions.admitGame(sessionValue, SETUP_GAME, 1, () -> now);
        sessions.releaseGame(SessionRegistry.digest(sessionValue), SETUP_GAME);
    }
}
