package ua.kostenko.battleship.app.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "battleship")
public record BattleshipProperties(
        @NotNull(message = "battleship.idle-timeout-seconds is required")
        @Min(value = 1, message = "battleship.idle-timeout-seconds must be at least 1")
        Integer idleTimeoutSeconds,

        @NotNull(message = "battleship.max-game-duration-seconds is required")
        @Min(value = 1, message = "battleship.max-game-duration-seconds must be at least 1")
        Integer maxGameDurationSeconds,

        @NotNull(message = "battleship.result-retention-seconds is required")
        @Min(value = 1, message = "battleship.result-retention-seconds must be at least 1")
        Integer resultRetentionSeconds,

        @NotNull(message = "battleship.invitation-lifetime-seconds is required")
        @Min(value = 1, message = "battleship.invitation-lifetime-seconds must be at least 1")
        Integer invitationLifetimeSeconds,

        @NotNull(message = "battleship.presence-interval-seconds is required")
        @Min(value = 1, message = "battleship.presence-interval-seconds must be at least 1")
        Integer presenceIntervalSeconds,

        @NotNull(message = "battleship.heartbeat-seconds is required")
        @Min(value = 1, message = "battleship.heartbeat-seconds must be at least 1")
        Integer heartbeatSeconds,

        @NotNull(message = "battleship.stream-max-lifetime-seconds is required")
        @Min(value = 1, message = "battleship.stream-max-lifetime-seconds must be at least 1")
        Integer streamMaxLifetimeSeconds,

        @NotNull(message = "battleship.max-concurrent-games is required")
        @Min(value = 1, message = "battleship.max-concurrent-games must be at least 1")
        Integer maxConcurrentGames,

        @NotNull(message = "battleship.max-concurrent-streams is required")
        @Min(value = 1, message = "battleship.max-concurrent-streams must be at least 1")
        Integer maxConcurrentStreams,

        @NotNull(message = "battleship.max-live-games-per-browser is required")
        @Min(value = 1, message = "battleship.max-live-games-per-browser must be at least 1")
        Integer maxLiveGamesPerBrowser,

        @NotNull(message = "battleship.max-request-body-bytes is required")
        @Min(value = 1, message = "battleship.max-request-body-bytes must be at least 1")
        Integer maxRequestBodyBytes,

        @NotNull(message = "battleship.random-arrangement-attempts is required")
        @Min(value = 1, message = "battleship.random-arrangement-attempts must be at least 1")
        Integer randomArrangementAttempts,

        @NotNull(message = "battleship.sweep-interval-seconds is required")
        @Min(value = 1, message = "battleship.sweep-interval-seconds must be at least 1")
        Integer sweepIntervalSeconds,

        @NotNull(message = "battleship.shutdown-drain-seconds is required")
        @Min(value = 1, message = "battleship.shutdown-drain-seconds must be at least 1")
        Integer shutdownDrainSeconds,

        @NotBlank(message = "battleship.public-base-url is required")
        String publicBaseUrl,

        @Valid @NotNull(message = "battleship.rate-limit is required")
        RateLimits rateLimit) {

    @PostConstruct
    void validatePublicBaseUrl() {
        final URI uri;
        try {
            uri = URI.create(publicBaseUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("battleship.public-base-url must be an absolute HTTP(S) URL", e);
        }

        var scheme = uri.getScheme();
        if (!uri.isAbsolute()
                || scheme == null
                || !(scheme.toLowerCase(Locale.ROOT).equals("http")
                        || scheme.toLowerCase(Locale.ROOT).equals("https"))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "battleship.public-base-url must be absolute HTTP(S), with a host and no credentials, query, or fragment");
        }
    }

    public record RateLimits(
            @NotNull(message = "battleship.rate-limit.create-game-per-minute is required")
            @Min(value = 1, message = "battleship.rate-limit.create-game-per-minute must be at least 1")
            Integer createGamePerMinute,

            @NotNull(message = "battleship.rate-limit.join-per-minute is required")
            @Min(value = 1, message = "battleship.rate-limit.join-per-minute must be at least 1")
            Integer joinPerMinute,

            @NotNull(message = "battleship.rate-limit.commands-per-minute is required")
            @Min(value = 1, message = "battleship.rate-limit.commands-per-minute must be at least 1")
            Integer commandsPerMinute,

            @NotNull(message = "battleship.rate-limit.read-game-per-minute is required")
            @Min(value = 1, message = "battleship.rate-limit.read-game-per-minute must be at least 1")
            Integer readGamePerMinute,

            @NotNull(message = "battleship.rate-limit.presence-per-minute is required")
            @Min(value = 1, message = "battleship.rate-limit.presence-per-minute must be at least 1")
            Integer presencePerMinute,

            @NotNull(message = "battleship.rate-limit.stream-open-per-minute is required")
            @Min(value = 1, message = "battleship.rate-limit.stream-open-per-minute must be at least 1")
            Integer streamOpenPerMinute,

            @NotNull(message = "battleship.rate-limit.replace-invitation-per-minute is required")
            @Min(value = 1, message = "battleship.rate-limit.replace-invitation-per-minute must be at least 1")
            Integer replaceInvitationPerMinute,

            @NotNull(message = "battleship.rate-limit.leave-per-minute is required")
            @Min(value = 1, message = "battleship.rate-limit.leave-per-minute must be at least 1")
            Integer leavePerMinute) {}
}
