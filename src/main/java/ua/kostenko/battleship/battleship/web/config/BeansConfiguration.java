package ua.kostenko.battleship.battleship.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.kostenko.battleship.battleship.logic.api.GameControllerV2Api;
import ua.kostenko.battleship.battleship.logic.api.GameControllerV2Impl;
import ua.kostenko.battleship.battleship.logic.api.IdGenerator;
import ua.kostenko.battleship.battleship.logic.api.IdGeneratorImpl;
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
    public GameControllerV2Api gameControllerV2Api(Persistence persistence, IdGenerator idGenerator) {
        return new GameControllerV2Impl(persistence, idGenerator);
    }
}
