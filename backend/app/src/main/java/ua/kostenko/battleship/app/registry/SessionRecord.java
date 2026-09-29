package ua.kostenko.battleship.app.registry;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionRecord {
    private final String digest;
    private final Instant createdAt;
    private Instant lastSeenAt;
    private final Set<String> liveGames = ConcurrentHashMap.newKeySet();

    SessionRecord(String digest, Instant now) {
        this.digest = digest;
        createdAt = now;
        lastSeenAt = now;
    }

    public String digest() {
        return digest;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastSeenAt() {
        return lastSeenAt;
    }

    void touch(Instant now) {
        lastSeenAt = now;
    }

    public Set<String> liveGames() {
        return Set.copyOf(liveGames);
    }

    void attachGame(String gameId) {
        liveGames.add(gameId);
    }
}
