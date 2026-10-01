package ua.kostenko.battleship.app;

import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import ua.kostenko.battleship.app.config.BattleshipProperties;
import ua.kostenko.battleship.application.port.SecretGenerator;
import ua.kostenko.battleship.application.port.TimeSource;
import ua.kostenko.battleship.application.projection.SnapshotProjector;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.application.usecase.CreateGameUseCase;
import ua.kostenko.battleship.application.usecase.ExpireGamesUseCase;
import ua.kostenko.battleship.application.usecase.GetGameUseCase;
import ua.kostenko.battleship.application.usecase.JoinGameUseCase;
import ua.kostenko.battleship.application.usecase.LeaveGameUseCase;
import ua.kostenko.battleship.application.usecase.ReplaceInvitationUseCase;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class BattleshipApplication {
    @Bean
    GameRegistry gameRegistry(BattleshipProperties properties) {
        return new GameRegistry(properties.maxConcurrentGames());
    }

    @Bean
    SessionRegistry sessionRegistry(BattleshipProperties properties) {
        return new SessionRegistry(properties.maxConcurrentGames() * 2);
    }

    @Bean
    SnapshotProjector snapshotProjector() {
        return new SnapshotProjector();
    }

    @Bean
    CreateGameUseCase createGameUseCase(
            GameRegistry games,
            SessionRegistry sessions,
            SecretGenerator secrets,
            TimeSource time,
            SnapshotProjector projector,
            BattleshipProperties properties) {
        return new CreateGameUseCase(
                games,
                sessions,
                secrets,
                time,
                projector,
                properties.maxLiveGamesPerBrowser(),
                Duration.ofSeconds(properties.idleTimeoutSeconds()),
                Duration.ofSeconds(properties.maxGameDurationSeconds()),
                Duration.ofSeconds(properties.invitationLifetimeSeconds()),
                Duration.ofSeconds(properties.resultRetentionSeconds()),
                properties.publicBaseUrl());
    }

    @Bean
    GetGameUseCase getGameUseCase(
            GameRegistry games,
            SessionRegistry sessions,
            TimeSource time,
            SnapshotProjector projector,
            BattleshipProperties properties) {
        return new GetGameUseCase(
                games, sessions, time, projector, Duration.ofSeconds(properties.resultRetentionSeconds()));
    }

    @Bean
    JoinGameUseCase joinGameUseCase(
            GameRegistry games,
            SessionRegistry sessions,
            SecretGenerator secrets,
            TimeSource time,
            SnapshotProjector projector,
            BattleshipProperties properties) {
        return new JoinGameUseCase(
                games,
                sessions,
                secrets,
                time,
                projector,
                properties.maxLiveGamesPerBrowser(),
                Duration.ofSeconds(properties.idleTimeoutSeconds()),
                Duration.ofSeconds(properties.resultRetentionSeconds()));
    }

    @Bean
    ReplaceInvitationUseCase replaceInvitationUseCase(
            GameRegistry games,
            SessionRegistry sessions,
            SecretGenerator secrets,
            TimeSource time,
            SnapshotProjector projector,
            BattleshipProperties properties) {
        return new ReplaceInvitationUseCase(
                games,
                sessions,
                secrets,
                time,
                projector,
                Duration.ofSeconds(properties.invitationLifetimeSeconds()),
                Duration.ofSeconds(properties.resultRetentionSeconds()));
    }

    @Bean
    ExpireGamesUseCase expireGamesUseCase(
            GameRegistry games, SessionRegistry sessions, BattleshipProperties properties) {
        return new ExpireGamesUseCase(games, sessions, Duration.ofSeconds(properties.resultRetentionSeconds()));
    }

    @Bean
    LeaveGameUseCase leaveGameUseCase(
            GameRegistry games, SessionRegistry sessions, TimeSource time, BattleshipProperties properties) {
        return new LeaveGameUseCase(games, sessions, time, Duration.ofSeconds(properties.resultRetentionSeconds()));
    }

    public static void main(String[] args) {
        SpringApplication.run(BattleshipApplication.class, args);
    }
}
