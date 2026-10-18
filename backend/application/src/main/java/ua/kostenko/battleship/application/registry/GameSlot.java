package ua.kostenko.battleship.application.registry;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import ua.kostenko.battleship.application.projection.SnapshotContext;
import ua.kostenko.battleship.application.usecase.ExpiryPolicy;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Seat;

public final class GameSlot {
    private final ReentrantLock lock = new ReentrantLock();
    private final String gameId;
    private final String publicBaseUrl;
    private GameState state;
    private String hostSessionDigest;
    private String guestSessionDigest;
    private String invitationDigest;
    private String unusedInvitationSecret;
    private final Set<UUID> acceptedCommandIds = new HashSet<>();
    private Instant idleDeadline;
    private final Instant absoluteDeadline;
    private Instant invitationDeadline;
    private final Map<Seat, Instant> presenceNotBefore = new EnumMap<>(Seat.class);
    private Instant terminalRetentionDeadline;
    private final Map<Seat, Long> connectedStreams = new EnumMap<>(Seat.class);

    public GameSlot(
            String gameId,
            GameState state,
            String hostSessionDigest,
            Instant idleDeadline,
            Instant absoluteDeadline,
            Instant invitationDeadline,
            String publicBaseUrl) {
        this.gameId = Objects.requireNonNull(gameId);
        this.state = Objects.requireNonNull(state);
        this.hostSessionDigest = Objects.requireNonNull(hostSessionDigest);
        this.idleDeadline = Objects.requireNonNull(idleDeadline);
        this.absoluteDeadline = Objects.requireNonNull(absoluteDeadline);
        this.invitationDeadline = Objects.requireNonNull(invitationDeadline);
        this.publicBaseUrl = Objects.requireNonNull(publicBaseUrl);
    }

    ReentrantLock lock() {
        return lock;
    }

    public String gameId() {
        return gameId;
    }

    public GameState state() {
        return state;
    }

    public void replace(GameState next) {
        state = Objects.requireNonNull(next);
    }

    public Set<UUID> acceptedCommandIds() {
        return acceptedCommandIds;
    }

    public Instant idleDeadline() {
        return idleDeadline;
    }

    public void idleDeadline(Instant deadline) {
        idleDeadline = Objects.requireNonNull(deadline);
    }

    public Instant absoluteDeadline() {
        return absoluteDeadline;
    }

    public Instant invitationDeadline() {
        return invitationDeadline;
    }

    public void invitationDeadline(Instant deadline) {
        invitationDeadline = Objects.requireNonNull(deadline);
    }

    public Instant terminalRetentionDeadline() {
        return terminalRetentionDeadline;
    }

    public void terminalRetentionDeadline(Instant deadline) {
        terminalRetentionDeadline = deadline;
    }

    public Map<Seat, Instant> presenceNotBefore() {
        return presenceNotBefore;
    }

    /**
     * The stream that marks each seat connected, by the number {@code SubscribeUseCase} gave it: a seat is connected
     * exactly when it has an entry. Recording the newest stream lets a replaced stream's late close change nothing.
     */
    public Map<Seat, Long> connectedStreams() {
        return connectedStreams;
    }

    public String hostSessionDigest() {
        return hostSessionDigest;
    }

    public String guestSessionDigest() {
        return guestSessionDigest;
    }

    public void guestSessionDigest(String digest) {
        guestSessionDigest = digest;
    }

    /** Forgets the seat's session, so that browser reads the game as unavailable from now on. */
    public void clearSeat(Seat seat) {
        if (seat == Seat.HOST) hostSessionDigest = null;
        else guestSessionDigest = null;
    }

    public String invitationDigest() {
        return invitationDigest;
    }

    public void invitationDigest(String digest) {
        invitationDigest = digest;
    }

    public String unusedInvitationSecret() {
        return unusedInvitationSecret;
    }

    public void unusedInvitationSecret(String secret) {
        unusedInvitationSecret = secret;
    }

    public SnapshotContext contextFor(Seat seat, Instant now) {
        Instant expiry = terminalRetentionDeadline;
        if (expiry == null || (state.phase() != Phase.FINISHED && state.phase() != Phase.ABANDONED)) {
            expiry = ExpiryPolicy.playDeadline(this);
        }
        Instant invitationExpiry = invitationDeadline.isBefore(expiry) ? invitationDeadline : expiry;
        String invitationUrl =
                seat == Seat.HOST && unusedInvitationSecret != null && !ExpiryPolicy.reached(invitationExpiry, now)
                        ? publicBaseUrl.replaceAll("/+$", "") + "/join/" + gameId + "#invite=" + unusedInvitationSecret
                        : null;
        return new SnapshotContext(
                gameId,
                now,
                expiry,
                invitationUrl,
                invitationUrl == null ? null : invitationExpiry,
                connectedStreams.containsKey(Seat.HOST),
                connectedStreams.containsKey(Seat.GUEST));
    }
}
