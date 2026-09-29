package ua.kostenko.battleship.app.config;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public class EnvironmentOverrideProbe {
    public static void main(String[] args) {
        try (var context = new SpringApplicationBuilder(ua.kostenko.battleship.app.BattleshipApplication.class)
                .web(WebApplicationType.NONE)
                .run()) {
            var properties = context.getBean(BattleshipProperties.class);
            System.out.println("IDLE_TIMEOUT_SECONDS=" + properties.idleTimeoutSeconds());
        }
    }
}
