package ua.kostenko.battleship.application.projection;

import java.time.Instant;

public record SnapshotContext(
        String gameId,
        Instant serverTime,
        Instant expiresAt,
        String invitationUrl,
        Instant invitationExpiresAt,
        boolean hostConnected,
        boolean guestConnected) {}
