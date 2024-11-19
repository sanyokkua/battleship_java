package ua.kostenko.battleship.battleship.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.kostenko.battleship.battleship.logic.api.GameControllerApi;
import ua.kostenko.battleship.battleship.logic.api.IdGenerator;
import ua.kostenko.battleship.battleship.logic.api.impl.GameControllerApiImpl;
import ua.kostenko.battleship.battleship.logic.api.impl.IdGeneratorImpl;
import ua.kostenko.battleship.battleship.logic.persistence.InMemoryPersistence;
import ua.kostenko.battleship.battleship.logic.persistence.Persistence;

/**
 * Configuration class for Spring Beans in the Battleship game application.
 * <p>
 * The BeansConfiguration class defines the beans for persistence, ID generation, and the game controller API.
 * </p>
 *
 * @see Persistence
 * @see IdGenerator
 * @see GameControllerApi
 */
@Configuration
public class BeansConfiguration {

    /**
     * Defines the Persistence bean for the application.
     *
     * @return the InMemoryPersistence bean
     */
    @Bean
    public Persistence persistence() {
        return new InMemoryPersistence();
    }

    /**
     * Defines the IdGenerator bean for the application.
     *
     * @return the IdGeneratorImpl bean
     */
    @Bean
    public IdGenerator idGenerator() {
        return new IdGeneratorImpl();
    }

    /**
     * Defines the GameControllerApi bean for the application.
     *
     * @param persistence the persistence bean
     * @param idGenerator the ID generator bean
     * @return the GameControllerApiImpl bean
     */
    @Bean
    public GameControllerApi gameControllerV2Api(Persistence persistence, IdGenerator idGenerator) {
        return new GameControllerApiImpl(persistence, idGenerator);
    }
}
