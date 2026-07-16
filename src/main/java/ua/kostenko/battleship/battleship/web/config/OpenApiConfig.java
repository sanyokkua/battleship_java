package ua.kostenko.battleship.battleship.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for OpenAPI/Swagger metadata in the Battleship game application.
 * <p>
 * The OpenApiConfig class defines the top-level OpenAPI document info (title, description, version)
 * shown in Swagger UI.
 * </p>
 */
@Configuration
public class OpenApiConfig {

    /**
     * Defines the OpenAPI bean describing the Battleship API.
     *
     * @return the OpenAPI bean
     */
    @Bean
    public OpenAPI battleshipOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Battleship API")
                .description("REST API for the Battleship game backend (session, preparation, gameplay)")
                .version("2.0.0"));
    }
}
