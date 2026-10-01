package ua.kostenko.battleship.app;

import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import ua.kostenko.battleship.app.config.BattleshipProperties;
import ua.kostenko.battleship.application.port.TimeSource;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.application.usecase.ExpireGamesUseCase;
import ua.kostenko.battleship.application.usecase.LeaveGameUseCase;

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
