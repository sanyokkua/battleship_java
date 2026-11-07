package ua.kostenko.battleship.app.observability;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the operational events and says the one start-up fact: nothing survives a restart (R48). */
@Configuration(proxyBeanMethods = false)
class LoggingConfig {
    @Bean
    OperationalEvents operationalEvents() {
        return new OperationalEvents();
    }

    @Bean
    ApplicationRunner stateLostOnRestart(OperationalEvents events) {
        return arguments -> events.stateLostOnRestart();
    }
}
