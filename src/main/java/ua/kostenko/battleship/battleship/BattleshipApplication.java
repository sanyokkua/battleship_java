package ua.kostenko.battleship.battleship;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main class for the Battleship game application.
 * <p>
 * The BattleshipApplication class serves as the entry point for the Spring Boot application.
 * </p>
 */
@SpringBootApplication
public class BattleshipApplication {

    /**
     * Main method to run the Spring Boot application.
     *
     * @param args the command line arguments
     */
    static void main(String[] args) {
        SpringApplication.run(BattleshipApplication.class, args);
    }

}
