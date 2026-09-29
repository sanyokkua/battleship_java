package ua.kostenko.battleship.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import ua.kostenko.battleship.app.config.BattleshipProperties;
import ua.kostenko.battleship.application.registry.GameRegistry;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class BattleshipApplication {
    @Bean
    GameRegistry gameRegistry(BattleshipProperties properties) {
        return new GameRegistry(properties.maxConcurrentGames());
    }

    public static void main(String[] args) {
        SpringApplication.run(BattleshipApplication.class, args);
    }
}
