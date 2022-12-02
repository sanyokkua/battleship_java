package ua.kostenko.battleship.battleship.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.kostenko.battleship.battleship.logic.api.GameControllerApi;
import ua.kostenko.battleship.battleship.logic.api.IdGenerator;
import ua.kostenko.battleship.battleship.logic.api.impl.GameControllerApiImpl;
import ua.kostenko.battleship.battleship.logic.api.impl.IdGeneratorImpl;
import ua.kostenko.battleship.battleship.logic.persistence.InMemoryPersistence;
import ua.kostenko.battleship.battleship.logic.persistence.Persistence;

@Configuration
public class BeansConfiguration {

    @Bean
    public Persistence persistence() {
        return new InMemoryPersistence();
    }

    @Bean
    public IdGenerator idGenerator() {
        return new IdGeneratorImpl();
    }

    @Bean
    public GameControllerApi gameControllerV2Api(Persistence persistence, IdGenerator idGenerator) {
        return new GameControllerApiImpl(persistence, idGenerator);
    }
}
