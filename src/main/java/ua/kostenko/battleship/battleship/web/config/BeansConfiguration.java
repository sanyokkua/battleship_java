package ua.kostenko.battleship.battleship.web.config;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import ua.kostenko.battleship.battleship.logic.api.GameControllerApi;
import ua.kostenko.battleship.battleship.logic.api.IdGenerator;
import ua.kostenko.battleship.battleship.logic.api.impl.GameControllerApiImpl;
import ua.kostenko.battleship.battleship.logic.api.impl.IdGeneratorImpl;
import ua.kostenko.battleship.battleship.logic.persistence.InMemoryPersistence;
import ua.kostenko.battleship.battleship.logic.persistence.Persistence;
import ua.kostenko.battleship.battleship.web.sse.SessionEventBroadcaster;

/**
 * Configuration class for Spring Beans in the Battleship game application.
 * <p>
 * The BeansConfiguration class defines the beans for persistence, ID generation, the game controller
 * API, and the SSE session event broadcaster.
 * </p>
 *
 * @see Persistence
 * @see IdGenerator
 * @see GameControllerApi
 * @see SessionEventBroadcaster
 */
@Configuration
@EnableScheduling
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
     * @param persistence    the persistence bean
     * @param idGenerator    the ID generator bean
     * @param eventPublisher used to notify listeners (e.g. the SSE broadcaster) after a mutating
     *                       operation completes
     * @return the GameControllerApiImpl bean
     */
    @Bean
    public GameControllerApi gameControllerV2Api(
            Persistence persistence, IdGenerator idGenerator, ApplicationEventPublisher eventPublisher) {
        return new GameControllerApiImpl(persistence, idGenerator, eventPublisher);
    }

    /**
     * Defines the SessionEventBroadcaster bean, which fans out SSE push notifications to clients
     * subscribed to a session's state changes.
     *
     * @param controllerV2Api the game controller API, used to build per-player push payloads
     * @return the SessionEventBroadcaster bean
     */
    @Bean
    public SessionEventBroadcaster sessionEventBroadcaster(GameControllerApi controllerV2Api) {
        return new SessionEventBroadcaster(controllerV2Api);
    }
}
