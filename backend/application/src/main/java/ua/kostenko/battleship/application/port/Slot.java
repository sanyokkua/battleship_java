package ua.kostenko.battleship.application.port;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ua.kostenko.battleship.application.projection.SnapshotContext;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Seat;

public interface Slot {
    GameState state();

    void replace(GameState next);

    Set<UUID> acceptedCommandIds();

    Instant idleDeadline();

    void idleDeadline(Instant deadline);

    Instant absoluteDeadline();

    Instant invitationDeadline();

    void invitationDeadline(Instant deadline);

    Instant terminalRetentionDeadline();

    void terminalRetentionDeadline(Instant deadline);

    Map<Seat, Instant> presenceNotBefore();

    String hostSessionDigest();

    String guestSessionDigest();

    void guestSessionDigest(String digest);

    String invitationDigest();

    void invitationDigest(String digest);

    String unusedInvitationSecret();

    void unusedInvitationSecret(String secret);

    SnapshotContext contextFor(Seat seat, Instant now);
}
