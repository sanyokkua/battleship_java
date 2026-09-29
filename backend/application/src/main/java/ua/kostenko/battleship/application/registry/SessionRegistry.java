package ua.kostenko.battleship.application.registry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public final class SessionRegistry {
    private final int capacity;
    private final Map<String, SessionRecord> records = new HashMap<>();
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

    public SessionRecord register(String sessionValue, Instant now) {
        return registerDigest(digest(sessionValue), now);
    }

    public SessionRecord registerForGame(String sessionValue, String gameId, Instant now) {
        lock.lock();
        try {
            SessionRecord record = registerDigest(digest(sessionValue), now);
            record.attachGame(gameId);
            return record;
        } finally {
            lock.unlock();
        }
    }

    private SessionRecord registerDigest(String digest, Instant now) {
        lock.lock();
        try {
            SessionRecord current = records.get(digest);
            if (current != null) {
                current.touch(now);
                return current;
            }
            if (records.size() == capacity) {
                String oldest = records.entrySet().stream()
                        .filter(entry -> entry.getValue().liveGames().isEmpty())
                        .min((a, b) ->
                                a.getValue().lastSeenAt().compareTo(b.getValue().lastSeenAt()))
                        .map(Map.Entry::getKey)
                        .orElse(null);
                if (oldest == null) throw new CapacityExceededException("sessions", 1);
                records.remove(oldest);
            }
            SessionRecord created = new SessionRecord(digest, now);
            records.put(digest, created);
            return created;
        } finally {
            lock.unlock();
        }
    }

    public Optional<SessionRecord> find(String sessionValue) {
        return findDigest(digest(sessionValue));
    }

    public Optional<SessionRecord> findDigest(String digest) {
        lock.lock();
        try {
            return Optional.ofNullable(records.get(digest));
        } finally {
            lock.unlock();
        }
    }

    public void touch(String sessionValue, Instant now) {
        lock.lock();
        try {
            SessionRecord current = records.get(digest(sessionValue));
            if (current != null) current.touch(now);
        } finally {
            lock.unlock();
        }
    }

    public Set<String> digests() {
        lock.lock();
        try {
            return Set.copyOf(records.keySet());
        } finally {
            lock.unlock();
        }
    }
}
