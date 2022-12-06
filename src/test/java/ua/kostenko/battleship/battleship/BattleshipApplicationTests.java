package ua.kostenko.battleship.battleship;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEditionConfiguration;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipDirection;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.web.api.dtos.ParamCoordinateDto;
import ua.kostenko.battleship.battleship.web.api.dtos.gameplay.ResponseShotResultDto;
import ua.kostenko.battleship.battleship.web.api.dtos.preparation.*;
import ua.kostenko.battleship.battleship.web.api.dtos.session.*;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BattleshipApplicationTests {
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    private static Set<Coordinate> getCoordinates() {
        Set<Coordinate> coordinates = new HashSet<>();
        for (int i = 0; i < GameEditionConfiguration.NUMBER_OF_ROWS; i += 2) {
            for (int j = 0; j < GameEditionConfiguration.NUMBER_OF_COLUMNS; j += 5) {
                coordinates.add(Coordinate.of(i, j));
            }
        }
        return coordinates;
    }

    @BeforeEach
    void beforeEach() {
        baseUrl = "http://localhost:" + port + "";
    }

    @Test
    void get_editions_should_return_two_editions() {
        // GET         /api/v2/game/editions
        var url_get_editions = "%s/api/v2/game/editions".formatted(baseUrl);

        var response = restTemplate.exchange(url_get_editions,
                                             HttpMethod.GET,
                                             HttpEntity.EMPTY,
                                             ResponseAvailableGameEditionsDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        var responseBody = response.getBody();

        assertNotNull(responseBody);

        var gameEditions = responseBody.getGameEditions();

        assertEquals(2, gameEditions.size());
        assertTrue(gameEditions.contains(GameEdition.UKRAINIAN.name()));
        assertTrue(gameEditions.contains(GameEdition.MILTON_BRADLEY.name()));
    }

    @Test
    void post_sessions_should_return_SessionID() {
        //POST        /api/v2/game/sessions
        var url_post_sessions = "%s/api/v2/game/sessions".formatted(baseUrl);

        var requestBody = new HttpEntity<>(new ParamGameEditionDto(GameEdition.UKRAINIAN.name()));

        var response = this.restTemplate.exchange(url_post_sessions,
                                                  HttpMethod.POST,
                                                  requestBody,
                                                  ResponseCreatedSessionIdDto.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());

        var body = response.getBody();
        assertTrue(StringUtils.isNotBlank(body.getSessionId()));
    }

    @Test
    void post_players_should_return_createdPlayer() {
        //POST        /api/v2/game/sessions/{sessionId}/players
        var url_post_player = "%s/api/v2/game/sessions/{sessionId}/players".formatted(baseUrl);

        var sessionResponse = createNewGameSession();
        var playerRequestBody = new HttpEntity<>(new ParamPlayerNameDto("Player_1"));

        var response = restTemplate.exchange(url_post_player,
                                             HttpMethod.POST,
                                             playerRequestBody,
                                             ResponseCreatedPlayerDto.class,
                                             sessionResponse.getSessionId());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        var player = response.getBody();

        assertTrue(StringUtils.isNotBlank(player.getPlayerId()));
        assertTrue(StringUtils.isNotBlank(player.getPlayerName()));
        assertEquals("Player_1", player.getPlayerName());
    }

    @Test
    void put_ships_should_add_ship_to_player_field() {
        //PUT         /api/v2/game/sessions/{sessionId}/players/{playerId}/ships/{shipId}
        var url_put_add_ships =
                "%s/api/v2/game/sessions/{sessionId}/players/{playerId}/ships/{shipId}".formatted(baseUrl);

        var session = createNewGameSession();
        var player1 = createPlayerInSession(session, "Player_1");
        var player2 = createPlayerInSession(session, "Player_2");


        getCoordinates().forEach(coordinate -> {
            var player1Ships = getPreparationState(session, player1);
            var player2Ships = getPreparationState(session, player2);

            var player1AddShipRequest = new HttpEntity<>(new ParamShipDto(coordinate.row(),
                                                                          coordinate.column(),
                                                                          ShipDirection.HORIZONTAL.name()));
            var player2AddShipRequest = new HttpEntity<>(new ParamShipDto(coordinate.row(),
                                                                          coordinate.column(),
                                                                          ShipDirection.HORIZONTAL.name()));

            var player1Ship = new LinkedList<>(player1Ships.getShips()).pop();
            var player2Ship = new LinkedList<>(player2Ships.getShips()).pop();

            var player1ShipAdded = restTemplate.exchange(url_put_add_ships,
                                                         HttpMethod.PUT,
                                                         player1AddShipRequest,
                                                         ResponseShipAddedDto.class,
                                                         session.getSessionId(),
                                                         player1.getPlayerId(),
                                                         player1Ship.getShipId());
            assertNotNull(player1ShipAdded);
            assertNotNull(player1ShipAdded.getBody());
            assertEquals(HttpStatus.OK, player1ShipAdded.getStatusCode());
            assertEquals(player1Ship.getShipId(),
                         player1ShipAdded.getBody()
                                         .getShipId());

            var player2ShipAdded = restTemplate.exchange(url_put_add_ships,
                                                         HttpMethod.PUT,
                                                         player2AddShipRequest,
                                                         ResponseShipAddedDto.class,
                                                         session.getSessionId(),
                                                         player2.getPlayerId(),
                                                         player2Ship.getShipId());

            assertNotNull(player2ShipAdded);
            assertNotNull(player2ShipAdded.getBody());
            assertEquals(HttpStatus.OK, player2ShipAdded.getStatusCode());
            assertEquals(player2Ship.getShipId(),
                         player2ShipAdded.getBody()
                                         .getShipId());
        });

    }

    @Test
    void delete_ship_should_remove_ship_from_field() {
        //DELETE      /api/v2/game/sessions/{sessionId}/players/{playerId}/ships?delete
        var url_delete_ship = "%s/api/v2/game/sessions/{sessionId}/players/{playerId}/ships?delete".formatted(baseUrl);

        var session = createNewGameSession();
        var player1 = createPlayerInSession(session, "Player1");
        var player2 = createPlayerInSession(session, "Player2");
        addShipsForPlayer(session, player1);
        addShipsForPlayer(session, player2);


        var response = restTemplate.exchange(url_delete_ship,
                                             HttpMethod.DELETE,
                                             new HttpEntity<>(new ParamCoordinateDto(0, 0)),
                                             ResponseShipRemovedDto.class,
                                             session.getSessionId(),
                                             player1.getPlayerId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        var removed = response.getBody();
        assertTrue(removed.isDeleted());

        var responseEmpty = restTemplate.exchange(url_delete_ship,
                                                  HttpMethod.DELETE,
                                                  new HttpEntity<>(new ParamCoordinateDto(0, 0)),
                                                  ResponseShipRemovedDto.class,
                                                  session.getSessionId(),
                                                  player1.getPlayerId());

        assertEquals(HttpStatus.OK, responseEmpty.getStatusCode());
        assertNotNull(responseEmpty.getBody());

        var removedEmpty = responseEmpty.getBody();
        assertFalse(removedEmpty.isDeleted());
    }

    @Test
    void get_preparation_state_should_return_current_state_for_player() {
        //GET         /api/v2/game/sessions/{sessionId}/players/{playerId}/preparationState
        var url_get_available_ships =
                "%s/api/v2/game/sessions/{sessionId}/players/{playerId}/preparationState".formatted(baseUrl);

        var session = createNewGameSession();
        var player1 = createPlayerInSession(session, "Player1");
        createPlayerInSession(session, "Player2");


        var shipsResponse = restTemplate.exchange(url_get_available_ships,
                                                  HttpMethod.GET,
                                                  HttpEntity.EMPTY,
                                                  ResponsePreparationState.class,
                                                  session.getSessionId(),
                                                  player1.getPlayerId());

        assertEquals(HttpStatus.OK, shipsResponse.getStatusCode());
        assertNotNull(shipsResponse.getBody());
        var state = shipsResponse.getBody();
        assertEquals(10,
                     state.getShips()
                          .size());

        addShipsForPlayer(session, player1);

        shipsResponse = restTemplate.exchange(url_get_available_ships,
                                              HttpMethod.GET,
                                              HttpEntity.EMPTY,
                                              ResponsePreparationState.class,
                                              session.getSessionId(),
                                              player1.getPlayerId());

        assertEquals(HttpStatus.OK, shipsResponse.getStatusCode());
        assertNotNull(shipsResponse.getBody());
        state = shipsResponse.getBody();
        assertEquals(0,
                     state.getShips()
                          .size());

    }

    @Test
    void post_players_start_should_change_player_state() {
        //POST        /api/v2/game/sessions/{sessionId}/players/{playerId}/start
        var url_post_players_start = "%s/api/v2/game/sessions/{sessionId}/players/{playerId}/start".formatted(baseUrl);

        var session = createNewGameSession();
        var player_1 = createPlayerInSession(session, "Player_1");
        var player_2 = createPlayerInSession(session, "Player_2");
        addShipsForPlayer(session, player_1);
        addShipsForPlayer(session, player_2);

        var response = restTemplate.exchange(url_post_players_start,
                                             HttpMethod.POST,
                                             HttpEntity.EMPTY,
                                             ResponsePlayerReady.class,
                                             session.getSessionId(),
                                             player_1.getPlayerId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        var player = response.getBody();

        assertTrue(player.isReady());
    }

    @Test
    void post_player_field_shot_should_make_a_shot_by_cell() {
        //POST        /api/v2/game/sessions/{sessionId}/players/{playerId}/field/shot
        var url_post_players_field_shot =
                "%s/api/v2/game/sessions/{sessionId}/players/{playerId}/field/shot".formatted(baseUrl);

        var session = createNewGameSession();
        var player_1 = createPlayerInSession(session, "Player_1");
        var player_2 = createPlayerInSession(session, "Player_2");
        createGameplay(session, player_1, player_2);

        var response = restTemplate.exchange(url_post_players_field_shot,
                                             HttpMethod.POST,
                                             new HttpEntity<>(Coordinate.of(0, 0)),
                                             ResponseShotResultDto.class,
                                             session.getSessionId(),
                                             player_1.getPlayerId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(Set.of(ShotResult.HIT.name(), ShotResult.DESTROYED.name())
                      .contains(response.getBody()
                                        .getShotResult()));
    }

    private ResponseCreatedSessionIdDto createNewGameSession() {
        var url_post_player = "%s/api/v2/game/sessions".formatted(baseUrl);
        var requestBody = new ParamGameEditionDto(GameEdition.UKRAINIAN.name());
        return restTemplate.postForObject(url_post_player, requestBody, ResponseCreatedSessionIdDto.class);
    }

    private ResponseCreatedPlayerDto createPlayerInSession(
            ResponseCreatedSessionIdDto gameSessionIdDto, String playerName) {
        var url_post_player = "%s/api/v2/game/sessions/{sessionId}/players".formatted(baseUrl);
        var playerRequestBody = new ParamPlayerNameDto(playerName);
        return restTemplate.postForObject(url_post_player,
                                          playerRequestBody,
                                          ResponseCreatedPlayerDto.class,
                                          gameSessionIdDto.getSessionId());
    }

    private ResponsePreparationState getPreparationState(
            ResponseCreatedSessionIdDto gameSessionIdDto, ResponseCreatedPlayerDto playerDto) {
        //GET         /api/v2/game/sessions/{sessionId}/players/{playerId}/preparationState
        var url_get_available_ships =
                "%s/api/v2/game/sessions/{sessionId}/players/{playerId}/preparationState".formatted(baseUrl);
        return restTemplate.getForObject(url_get_available_ships,
                                         ResponsePreparationState.class,
                                         gameSessionIdDto.getSessionId(),
                                         playerDto.getPlayerId());
    }

    private void createGameplay(
            ResponseCreatedSessionIdDto gameSessionIdDto, ResponseCreatedPlayerDto player1,
            ResponseCreatedPlayerDto player2) {
        addShipsForPlayer(gameSessionIdDto, player1);
        addShipsForPlayer(gameSessionIdDto, player2);

        var url_post_players_start = "%s/api/v2/game/sessions/{sessionId}/players/{playerId}/start".formatted(baseUrl);

        restTemplate.postForObject(url_post_players_start,
                                   null,
                                   ResponseCreatedPlayerDto.class,
                                   gameSessionIdDto.getSessionId(),
                                   player1.getPlayerId());

        restTemplate.postForObject(url_post_players_start,
                                   null,
                                   ResponseCreatedPlayerDto.class,
                                   gameSessionIdDto.getSessionId(),
                                   player2.getPlayerId());
    }

    private void addShipsForPlayer(ResponseCreatedSessionIdDto gameSessionIdDto, ResponseCreatedPlayerDto playerDto) {
        var url_post_add_ships =
                "%s/api/v2/game/sessions/{sessionId}/players/{playerId}/ships/{shipId}".formatted(baseUrl);
        getCoordinates().forEach(coordinate -> {
            var playerShips = getPreparationState(gameSessionIdDto, playerDto);
            var playerShipAddRequestBody = new HttpEntity<>(new ParamShipDto(coordinate.row(),
                                                                             coordinate.column(),
                                                                             ShipDirection.HORIZONTAL.name()));
            var playerShipToAdd = new LinkedList<>(playerShips.getShips()).pop();
            restTemplate.exchange(url_post_add_ships,
                                  HttpMethod.PUT,
                                  playerShipAddRequestBody,
                                  ResponseShipAddedDto.class,
                                  gameSessionIdDto.getSessionId(),
                                  playerDto.getPlayerId(),
                                  playerShipToAdd.getShipId());
        });
    }

}
