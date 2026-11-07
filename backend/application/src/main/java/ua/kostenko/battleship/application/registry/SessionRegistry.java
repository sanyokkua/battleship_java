package ua.kostenko.battleship.application.registry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class SessionRegistry {
    private final int capacity;
    private final Map<String, SessionRecord> records = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public SessionRegistry(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Runs admission before changing session ownership, while the per-browser cap is locked. */
    public Instant admitGame(String sessionValue, String gameId, int maxLiveGames, Supplier<Instant> admission) {
        Objects.requireNonNull(admission);
        if (maxLiveGames < 1) throw new IllegalArgumentException("maxLiveGames must be positive");
        String digest = digest(sessionValue);
        lock.lock();
        try {
            SessionRecord current = records.get(digest);
            if (current != null && current.liveGames().size() >= maxLiveGames) throw new BrowserGameLimitException();
            String evictable = null;
            if (current == null && records.size() == capacity) {
                evictable = records.entrySet().stream()
                        .filter(entry -> entry.getValue().liveGames().isEmpty())
                        .min((a, b) ->
                                a.getValue().lastSeenAt().compareTo(b.getValue().lastSeenAt()))
                        .map(Map.Entry::getKey)
                        .orElseThrow(() -> new CapacityExceededException("sessions", 1));
            }
            Instant now = admission.get();
            if (evictable != null) records.remove(evictable);
            if (current == null) {
                current = new SessionRecord(digest, now);
                records.put(digest, current);
            } else {
                current.touch(now);
            }
            current.attachGame(gameId);
            return now;
        } finally {
            lock.unlock();
        }
    }

    public Optional<SessionRecord> find(String sessionValue) {
        return Optional.ofNullable(records.get(digest(sessionValue)));
    }

    /** Finds the session a protected request presents and marks it seen, so eviction drops the longest-idle one. */
    public Optional<SessionRecord> authenticate(String sessionValue, Instant now) {
        Optional<SessionRecord> session = find(sessionValue);
        session.ifPresent(found -> found.touch(now));
        return session;
    }

    /** Terminal ownership release deliberately avoids the admission lock while a game slot is held. */
    public void releaseGame(String digest, String gameId) {
        if (digest == null) return;
        SessionRecord session = records.get(digest);
        if (session != null) session.detachGame(gameId);
    }
}
