package ua.kostenko.battleship.battleship;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import ua.kostenko.battleship.battleship.logic.api.dtos.GameEditionsDto;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BattleshipApplicationTests {
    private static final String URL_EDITIONS = "/editions";
    private static final String URL_SESSIONS = "/sessions";
    private static final String URL_PLAYERS = "/sessions/{sessionId}/players";
    private static final String URL_ADD_SHIP = "/sessions/{sessionId}/players/{playerId}/ships/{shipId}";
    private static final String URL_DELETE_SHIP = "/sessions/{sessionId}/players/{playerId}/ships?delete";
    private static final String URL_AVAILABLE_SHIPS = "/sessions/{sessionId}/players/{playerId}/ships?available";
    private static final String URL_START = "/sessions/{sessionId}/players/{playerId}?start";
    private static final String URL_GET_PLAYER = "/sessions/{sessionId}/players/{playerId}";
    private static final String URL_GET_OPPONENT = "/sessions/{sessionId}/players/{playerId}?opponent";
    private static final String URL_GET_FIELD = "/sessions/{sessionId}/players/{playerId}/field";
    private static final String URL_GET_OPPONENT_FIELD = "/sessions/{sessionId}/players/{playerId}/field?opponent";
    private static final String URL_GET_ACTIVE_PLAYER = "/sessions/{sessionId}/players?active";
    private static final String URL_MAKE_SHOT = "/sessions/{sessionId}/players/{playerId}/field?shot";
    private static final String URL_GET_UNDAMAGED_CELLS = "/sessions/{sessionId}/players/{playerId}/cells";
    private static final String URL_GET_ALIVE_SHIPS = "/sessions/{sessionId}/players/{playerId}/ships?NotDestroyed";
    private static final String URL_WINNER = "/sessions/{sessionId}/winner";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    String buildUrl(String endpoint) {
        var builtUrl = baseUrl + endpoint;
        System.out.println("URL: " + builtUrl);
        return builtUrl;
    }

    @BeforeEach
    void beforeEach() {
        baseUrl = "http://localhost:" + port + "/api/game";
    }

    @Test
    void getEditionsShouldReturn2editions() throws Exception {
        var response = this.restTemplate.getForObject(buildUrl(URL_EDITIONS), GameEditionsDto.class);
        assertEquals(2,
                     response.getGameEditions()
                             .size());
        assertTrue(response.getGameEditions()
                           .contains(GameEdition.UKRAINIAN));
        assertTrue(response.getGameEditions()
                           .contains(GameEdition.MILTON_BRADLEY));
    }
}
