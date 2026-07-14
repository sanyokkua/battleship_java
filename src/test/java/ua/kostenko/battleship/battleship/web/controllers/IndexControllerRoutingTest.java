package ua.kostenko.battleship.battleship.web.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ua.kostenko.battleship.battleship.logic.api.GameControllerApi;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.web.controllers.rest.GameSessionCommonRestController;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest} coverage for {@link IndexController}'s
 * SPA-fallback forwarding.
 * <p>
 * {@link GameSessionCommonRestController} is loaded alongside {@link IndexController} in the same MockMvc
 * dispatcher so we can empirically confirm that the new extensionless catch-all mappings
 * ({@code /{path:[^\.]*}} and {@code /**}{@code /{path:[^\.]*}}) do not shadow the more specific
 * {@code /api/v2/game/**} REST mappings, per Spring's most-specific-pattern-wins resolution.
 */
@WebMvcTest(controllers = {IndexController.class, GameSessionCommonRestController.class})
class IndexControllerRoutingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameControllerApi controllerV2Api;

    /**
     * All seven client-side (React Router) routes from {@code frontend/src/routing/AppRoutes.tsx} must resolve
     * to the same {@code index} view on a direct load/refresh, not a 404.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/", "/new", "/join", "/game/wait", "/game/preparation", "/game/gameplay",
            "/game/results"})
    void clientSideRoute_directLoad_forwardsToIndexView(final String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<div id=\"root\">")));
    }

    /**
     * A real REST endpoint under {@code /api/v2/game/**} must still be routed to its own controller and must not
     * be swallowed by the new catch-all index forwarding.
     */
    @Test
    void apiEndpoint_isNotShadowedByIndexCatchAll() throws Exception {
        when(controllerV2Api.getAvailableGameEditions()).thenReturn(List.of(GameEdition.UKRAINIAN));

        mockMvc.perform(get("/api/v2/game/editions"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("UKRAINIAN")));
    }

    /**
     * A path with a file extension but no matching static resource (no {@code favicon.ico} exists in this
     * project) must still 404 cleanly, proving the {@code [^\.]*} regex correctly excludes paths whose last
     * segment contains a dot so real static assets keep resolving via normal static-resource handling.
     */
    @Test
    void extensionPath_withNoMatchingResource_returns404() throws Exception {
        mockMvc.perform(get("/favicon.ico")).andExpect(status().isNotFound());
    }

    // Note: /swagger-ui/index.html and /v3/api-docs are SpringDoc-managed and registered via SpringDoc's own
    // auto-configuration/controllers, which are not part of this @WebMvcTest slice (only IndexController and
    // GameSessionCommonRestController are loaded here). Exercising those would require a full @SpringBootTest
    // context, which is out of scope for this controller-level test, so this check is skipped rather than
    // over-invested in.
}
