package ua.kostenko.battleship.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BattleshipApplication {
    public static void main(String[] args) {
        SpringApplication.run(BattleshipApplication.class, args);
    }
}
