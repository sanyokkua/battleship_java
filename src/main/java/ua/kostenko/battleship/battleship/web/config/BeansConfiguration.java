package ua.kostenko.battleship.battleship.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.kostenko.battleship.battleship.logic.api.*;
import ua.kostenko.battleship.battleship.logic.persistence.InMemoryPersistence;
import ua.kostenko.battleship.battleship.logic.persistence.Persistence;

@Configuration
public class BeansConfiguration {

    @Bean
    public Persistence persistence() {
        return new InMemoryPersistence();
    }

    @Bean
    public ControllerApi controller(Persistence persistence) {
        return new ControllerApiImpl(persistence);
    }

    @Bean
    public IdGenerator idGenerator() {
        return new IdGeneratorImpl();
    }

    @Bean
    public GameControllerV2Api gameControllerV2Api(Persistence persistence, IdGenerator idGenerator) {
        return new GameControllerV2Impl(persistence, idGenerator);
    }
}
