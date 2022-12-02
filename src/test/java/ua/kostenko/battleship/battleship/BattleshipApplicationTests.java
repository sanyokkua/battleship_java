package ua.kostenko.battleship.battleship;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BattleshipApplicationTests {
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    //    private static Set<Coordinate> getCoordinates() {
    //        Set<Coordinate> coordinates = new HashSet<>();
    //        for (int i = 0; i < GameEditionConfiguration.NUMBER_OF_ROWS; i += 2) {
    //            for (int j = 0; j < GameEditionConfiguration.NUMBER_OF_COLUMNS; j += 5) {
    //                coordinates.add(Coordinate.of(i, j));
    //            }
    //        }
    //        return coordinates;
    //    }

    //    @BeforeEach
    //    void beforeEach() {
    //        baseUrl = "http://localhost:" + port + "";
    //    }

    //    @Test
    //    void get_editions_should_return_two_editions() {
    //        // GET         /api/game/editions
    //        var url_get_editions = "%s/api/game/editions".formatted(baseUrl);
    //
    //        var response = restTemplate.exchange(url_get_editions, HttpMethod.GET, HttpEntity.EMPTY,
    //        GameEditionsDto.class);
    //
    //        assertEquals(HttpStatus.OK, response.getStatusCode());
    //
    //        var responseBody = response.getBody();
    //
    //        assertNotNull(responseBody);
    //
    //        var gameEditions = responseBody.getGameEditions();
    //
    //        assertEquals(2, gameEditions.size());
    //        assertTrue(gameEditions.contains(GameEdition.UKRAINIAN));
    //        assertTrue(gameEditions.contains(GameEdition.MILTON_BRADLEY));
    //    }

    //    @Test
    //    void post_sessions_should_return_SessionID() {
    //        //POST        /api/game/sessions
    //        var url_post_sessions = "%s/api/game/sessions".formatted(baseUrl);
    //
    //        var requestBody = new HttpEntity<>(new GameEditionDto(GameEdition.UKRAINIAN.name()));
    //
    //        var response =
    //                this.restTemplate.exchange(url_post_sessions, HttpMethod.POST, requestBody, GameSessionIdDto
    //                .class);
    //
    //        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    //        assertNotNull(response.getBody());
    //
    //        var body = response.getBody();
    //        assertTrue(StringUtils.isNotBlank(body.getGameSessionId()));
    //    }

    //    @Test
    //    void post_players_should_return_createdPlayer() {
    //        //POST        /api/game/sessions/{sessionId}/players
    //        var url_post_player = "%s/api/game/sessions/{sessionId}/players".formatted(baseUrl);
    //
    //        var sessionResponse = createNewGameSession();
    //        var playerRequestBody = new HttpEntity<>(new PlayerNameDto("Player_1"));
    //
    //        var response = restTemplate.exchange(url_post_player,
    //                                             HttpMethod.POST,
    //                                             playerRequestBody,
    //                                             PlayerDto.class,
    //                                             sessionResponse.getGameSessionId());
    //
    //        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    //        assertNotNull(response.getBody());
    //        var player = response.getBody();
    //
    //        assertTrue(StringUtils.isNotBlank(player.getPlayerId()));
    //        assertTrue(StringUtils.isNotBlank(player.getPlayerName()));
    //        assertNotNull(player.getField());
    //        assertNotNull(player.getAllPlayerShips());
    //        assertNotNull(player.getShipsNotOnTheField());
    //        assertEquals("Player_1", player.getPlayerName());
    //    }

    //    @Test
    //    void put_ships_should_add_ship_to_player_field() {
    //        //PUT         /api/game/sessions/{sessionId}/players/{playerId}/ships/{shipId}
    //        var url_put_add_ships = "%s/api/game/sessions/{sessionId}/players/{playerId}/ships/{shipId}".formatted
    //        (baseUrl);
    //
    //        var session = createNewGameSession();
    //        var player1 = createPlayerInSession(session, "Player_1");
    //        var player2 = createPlayerInSession(session, "Player_2");
    //
    //
    //        getCoordinates().forEach(coordinate -> {
    //            var player1Ships = getAvailableShips(session, player1);
    //            var player2Ships = getAvailableShips(session, player2);
    //
    //            var player1AddShipRequest =
    //                    new HttpEntity<>(new AddShipToFieldBody(coordinate, ShipDirection.HORIZONTAL.name()));
    //            var player2AddShipRequest =
    //                    new HttpEntity<>(new AddShipToFieldBody(coordinate, ShipDirection.HORIZONTAL.name()));
    //
    //            var player1Ship = player1Ships.pop();
    //            var player2Ship = player2Ships.pop();
    //
    //            var player1ShipAdded = restTemplate.exchange(url_put_add_ships,
    //                                                         HttpMethod.PUT,
    //                                                         player1AddShipRequest,
    //                                                         ShipDto.class,
    //                                                         session.getGameSessionId(),
    //                                                         player1.getPlayerId(),
    //                                                         player1Ship.getShipId());
    //            assertNotNull(player1ShipAdded);
    //            assertNotNull(player1ShipAdded.getBody());
    //            assertEquals(HttpStatus.OK, player1ShipAdded.getStatusCode());
    //            assertEquals(player1Ship.getShipId(),
    //                         player1ShipAdded.getBody()
    //                                         .getShipId());
    //
    //            var player2ShipAdded = restTemplate.exchange(url_put_add_ships,
    //                                                         HttpMethod.PUT,
    //                                                         player2AddShipRequest,
    //                                                         ShipDto.class,
    //                                                         session.getGameSessionId(),
    //                                                         player2.getPlayerId(),
    //                                                         player2Ship.getShipId());
    //
    //            assertNotNull(player2ShipAdded);
    //            assertNotNull(player2ShipAdded.getBody());
    //            assertEquals(HttpStatus.OK, player2ShipAdded.getStatusCode());
    //            assertEquals(player2Ship.getShipId(),
    //                         player2ShipAdded.getBody()
    //                                         .getShipId());
    //        });
    //
    //    }

    //    @Test
    //    void delete_ship_should_remove_ship_from_field() {
    //        //DELETE      /api/game/sessions/{sessionId}/players/{playerId}/ships?delete
    //        var url_delete_ship = "%s/api/game/sessions/{sessionId}/players/{playerId}/ships?delete".formatted
    //        (baseUrl);
    //
    //        var session = createNewGameSession();
    //        var player1 = createPlayerInSession(session, "Player1");
    //        var player2 = createPlayerInSession(session, "Player2");
    //        addShipsForPlayer(session, player1);
    //        addShipsForPlayer(session, player2);
    //
    //
    //        var response = restTemplate.exchange(url_delete_ship,
    //                                             HttpMethod.DELETE,
    //                                             new HttpEntity<>(Coordinate.of(0, 0)),
    //                                             RemovedShipDto.class,
    //                                             session.getGameSessionId(),
    //                                             player1.getPlayerId());
    //        assertEquals(HttpStatus.OK, response.getStatusCode());
    //        assertNotNull(response.getBody());
    //
    //        var removed = response.getBody();
    //        assertTrue(StringUtils.isNotBlank(removed.getRemovedShipId()));
    //
    //        var responseEmpty = restTemplate.exchange(url_delete_ship,
    //                                                  HttpMethod.DELETE,
    //                                                  new HttpEntity<>(Coordinate.of(0, 0)),
    //                                                  RemovedShipDto.class,
    //                                                  session.getGameSessionId(),
    //                                                  player1.getPlayerId());
    //
    //        assertEquals(HttpStatus.OK, responseEmpty.getStatusCode());
    //        assertNotNull(responseEmpty.getBody());
    //
    //        var removedEmpty = responseEmpty.getBody();
    //        assertTrue(StringUtils.isBlank(removedEmpty.getRemovedShipId()));
    //    }

    //    @Test
    //    void get_ships_available_should_return_list_of_ships() {
    //        //GET         /api/game/sessions/{sessionId}/players/{playerId}/ships?available
    //        var url_get_available_ships =
    //                "%s/api/game/sessions/{sessionId}/players/{playerId}/ships?available".formatted(baseUrl);
    //
    //        var session = createNewGameSession();
    //        var player1 = createPlayerInSession(session, "Player1");
    //        createPlayerInSession(session, "Player2");
    //
    //
    //        var shipsResponse = restTemplate.exchange(url_get_available_ships,
    //                                                  HttpMethod.GET,
    //                                                  HttpEntity.EMPTY,
    //                                                  ShipDto[].class,
    //                                                  session.getGameSessionId(),
    //                                                  player1.getPlayerId());
    //
    //        assertEquals(HttpStatus.OK, shipsResponse.getStatusCode());
    //        assertNotNull(shipsResponse.getBody());
    //        var ships = shipsResponse.getBody();
    //        assertEquals(10, ships.length);
    //
    //        addShipsForPlayer(session, player1);
    //
    //        shipsResponse = restTemplate.exchange(url_get_available_ships,
    //                                              HttpMethod.GET,
    //                                              HttpEntity.EMPTY,
    //                                              ShipDto[].class,
    //                                              session.getGameSessionId(),
    //                                              player1.getPlayerId());
    //
    //        assertEquals(HttpStatus.OK, shipsResponse.getStatusCode());
    //        assertNotNull(shipsResponse.getBody());
    //        ships = shipsResponse.getBody();
    //        assertEquals(0, ships.length);
    //
    //    }

    //    @Test
    //    void post_players_start_should_change_player_state() {
    //        //POST        /api/game/sessions/{sessionId}/players/{playerId}?start
    //        var url_post_players_start = "%s/api/game/sessions/{sessionId}/players/{playerId}?start".formatted
    //        (baseUrl);
    //
    //        var session = createNewGameSession();
    //        var player_1 = createPlayerInSession(session, "Player_1");
    //        var player_2 = createPlayerInSession(session, "Player_2");
    //        addShipsForPlayer(session, player_1);
    //        addShipsForPlayer(session, player_2);
    //
    //        var response = restTemplate.exchange(url_post_players_start,
    //                                             HttpMethod.POST,
    //                                             HttpEntity.EMPTY,
    //                                             PlayerDto.class,
    //                                             session.getGameSessionId(),
    //                                             player_1.getPlayerId());
    //
    //        assertEquals(HttpStatus.OK, response.getStatusCode());
    //        assertNotNull(response.getBody());
    //
    //        var player = response.getBody();
    //
    //        assertEquals(player_1.getPlayerId(), player.getPlayerId());
    //        assertTrue(player.isReady());
    //    }

    //    @Test
    //    void get_players_player_id_should_return_player() {
    //        //GET         /api/game/sessions/{sessionId}/players/{playerId}
    //        var url_get_players = "%s/api/game/sessions/{sessionId}/players/{playerId}".formatted(baseUrl);
    //
    //        var session = createNewGameSession();
    //        var player_1 = createPlayerInSession(session, "Player_1");
    //        var player_2 = createPlayerInSession(session, "Player_2");
    //
    //        var responsePlayer1 = restTemplate.exchange(url_get_players,
    //                                                    HttpMethod.GET,
    //                                                    HttpEntity.EMPTY,
    //                                                    PlayerDto.class,
    //                                                    session.getGameSessionId(),
    //                                                    player_1.getPlayerId());
    //
    //        assertEquals(HttpStatus.OK, responsePlayer1.getStatusCode());
    //        assertNotNull(responsePlayer1.getBody());
    //        var player1Body = responsePlayer1.getBody();
    //        assertEquals(player_1.getPlayerId(), player1Body.getPlayerId());
    //
    //        var responsePlayer2 = restTemplate.exchange(url_get_players,
    //                                                    HttpMethod.GET,
    //                                                    HttpEntity.EMPTY,
    //                                                    PlayerDto.class,
    //                                                    session.getGameSessionId(),
    //                                                    player_2.getPlayerId());
    //
    //        assertEquals(HttpStatus.OK, responsePlayer2.getStatusCode());
    //        assertNotNull(responsePlayer2.getBody());
    //        var player2Body = responsePlayer2.getBody();
    //        assertEquals(player_2.getPlayerId(), player2Body.getPlayerId());
    //    }

    //    @Test
    //    void get_players_player_id_opponent_should_return_opponent() {
    //        //GET         /api/game/sessions/{sessionId}/players/{playerId}?opponent
    //        var url_get_players_opponent =
    //                "%s/api/game/sessions/{sessionId}/players/{playerId}?opponent".formatted(baseUrl);
    //
    //        var session = createNewGameSession();
    //        var player_1 = createPlayerInSession(session, "Player_1");
    //        var player_2 = createPlayerInSession(session, "Player_2");
    //
    //        var responsePlayer1 = restTemplate.exchange(url_get_players_opponent,
    //                                                    HttpMethod.GET,
    //                                                    HttpEntity.EMPTY,
    //                                                    PlayerBaseInfoDto.class,
    //                                                    session.getGameSessionId(),
    //                                                    player_1.getPlayerId());
    //
    //        assertEquals(HttpStatus.OK, responsePlayer1.getStatusCode());
    //        assertNotNull(responsePlayer1.getBody());
    //        var opponentIsPlayer2 = responsePlayer1.getBody();
    //        assertEquals(player_2.getPlayerName(), opponentIsPlayer2.getPlayerName());
    //
    //        var responsePlayer2 = restTemplate.exchange(url_get_players_opponent,
    //                                                    HttpMethod.GET,
    //                                                    HttpEntity.EMPTY,
    //                                                    PlayerBaseInfoDto.class,
    //                                                    session.getGameSessionId(),
    //                                                    player_2.getPlayerId());
    //
    //        assertEquals(HttpStatus.OK, responsePlayer2.getStatusCode());
    //        assertNotNull(responsePlayer2.getBody());
    //        var opponentIsPlayer1 = responsePlayer2.getBody();
    //        assertEquals(player_1.getPlayerName(), opponentIsPlayer1.getPlayerName());
    //    }

    //    @Test
    //    void get_players_player_id_field_should_return_field() {
    //        //GET         /api/game/sessions/{sessionId}/players/{playerId}/field
    //        var url_get_players_field = "%s/api/game/sessions/{sessionId}/players/{playerId}/field".formatted
    //        (baseUrl);
    //
    //        var session = createNewGameSession();
    //        var player_1 = createPlayerInSession(session, "Player_1");
    //        createPlayerInSession(session, "Player_2");
    //
    //        var responsePlayer1 = restTemplate.exchange(url_get_players_field,
    //                                                    HttpMethod.GET,
    //                                                    HttpEntity.EMPTY,
    //                                                    CellDto[][].class,
    //                                                    session.getGameSessionId(),
    //                                                    player_1.getPlayerId());
    //
    //        assertEquals(HttpStatus.OK, responsePlayer1.getStatusCode());
    //        assertNotNull(responsePlayer1.getBody());
    //    }

    //    @Test
    //    void get_players_player_id_field_opponent_should_return_field() {
    //        //GET         /api/game/sessions/{sessionId}/players/{playerId}/field?opponent
    //        var url_get_players_field_opponent =
    //                "%s/api/game/sessions/{sessionId}/players/{playerId}/field?opponent".formatted(baseUrl);
    //
    //        var session = createNewGameSession();
    //        var player_1 = createPlayerInSession(session, "Player_1");
    //        createPlayerInSession(session, "Player_2");
    //
    //        var responsePlayer1 = restTemplate.exchange(url_get_players_field_opponent,
    //                                                    HttpMethod.GET,
    //                                                    HttpEntity.EMPTY,
    //                                                    CellDto[][].class,
    //                                                    session.getGameSessionId(),
    //                                                    player_1.getPlayerId());
    //
    //        assertEquals(HttpStatus.OK, responsePlayer1.getStatusCode());
    //        assertNotNull(responsePlayer1.getBody());
    //    }

    //    @Test
    //    void get_players_active_should_return_active_player() {
    //        //GET         /api/game/sessions/{sessionId}/players?active
    //        var url_get_players_active = "%s/api/game/sessions/{sessionId}/players?active".formatted(baseUrl);
    //
    //        var session = createNewGameSession();
    //        var player_1 = createPlayerInSession(session, "Player_1");
    //        var player_2 = createPlayerInSession(session, "Player_2");
    //        createGameplay(session, player_1, player_2);
    //
    //        var response = restTemplate.exchange(url_get_players_active,
    //                                             HttpMethod.GET,
    //                                             HttpEntity.EMPTY,
    //                                             PlayerBaseInfoDto.class,
    //                                             session.getGameSessionId(),
    //                                             player_1.getPlayerId());
    //
    //        assertEquals(HttpStatus.OK, response.getStatusCode());
    //        assertNotNull(response.getBody());
    //        var player = response.getBody();
    //        assertEquals("Player_1", player.getPlayerName());
    //        assertTrue(player.isActive());
    //    }

    //    @Test
    //    void post_player_field_shot_should_make_a_shot_by_cell() {
    //        //POST        /api/game/sessions/{sessionId}/players/{playerId}/field?shot
    //        var url_post_players_field_shot =
    //                "%s/api/game/sessions/{sessionId}/players/{playerId}/field?shot".formatted(baseUrl);
    //
    //        var session = createNewGameSession();
    //        var player_1 = createPlayerInSession(session, "Player_1");
    //        var player_2 = createPlayerInSession(session, "Player_2");
    //        createGameplay(session, player_1, player_2);
    //
    //        var response = restTemplate.exchange(url_post_players_field_shot,
    //                                             HttpMethod.POST,
    //                                             new HttpEntity<>(Coordinate.of(0, 0)),
    //                                             ShotResultDto.class,
    //                                             session.getGameSessionId(),
    //                                             player_1.getPlayerId());
    //
    //        assertEquals(HttpStatus.OK, response.getStatusCode());
    //        assertNotNull(response.getBody());
    //        assertTrue(Set.of(ShotResult.HIT, ShotResult.DESTROYED)
    //                      .contains(response.getBody()
    //                                        .getShotResult()));
    //    }

    //    @Test
    //    void get_players_cells_should_return_number_of_cells_not_damaged() {
    //        //GET         /api/game/sessions/{sessionId}/players/{playerId}/cells
    //        var url_get_players_cells = "%s/api/game/sessions/{sessionId}/players/{playerId}/cells".formatted
    //        (baseUrl);
    //
    //        var session = createNewGameSession();
    //        var player_1 = createPlayerInSession(session, "Player_1");
    //        var player_2 = createPlayerInSession(session, "Player_2");
    //        createGameplay(session, player_1, player_2);
    //
    //        var response = restTemplate.exchange(url_get_players_cells,
    //                                             HttpMethod.GET,
    //                                             HttpEntity.EMPTY,
    //                                             UndamagedCellsDto.class,
    //                                             session.getGameSessionId(),
    //                                             player_1.getPlayerId());
    //
    //        assertEquals(HttpStatus.OK, response.getStatusCode());
    //        assertNotNull(response.getBody());
    //        assertEquals(100,
    //                     response.getBody()
    //                             .getNumberOfUndamagedCells());
    //    }

    //    @Test
    //    void get_players_ships_not_destroyed_should_return_number_of_alive_ships() {
    //        //GET         /api/game/sessions/{sessionId}/players/{playerId}/ships?NotDestroyed
    //        var url_get_players_ships_not_destroyed =
    //                "%s/api/game/sessions/{sessionId}/players/{playerId}/ships?NotDestroyed".formatted(baseUrl);
    //
    //        var session = createNewGameSession();
    //        var player_1 = createPlayerInSession(session, "Player_1");
    //        var player_2 = createPlayerInSession(session, "Player_2");
    //        createGameplay(session, player_1, player_2);
    //
    //        var response = restTemplate.exchange(url_get_players_ships_not_destroyed,
    //                                             HttpMethod.GET,
    //                                             HttpEntity.EMPTY,
    //                                             NumberOfAliveShipsDto.class,
    //                                             session.getGameSessionId(),
    //                                             player_1.getPlayerId());
    //
    //        assertEquals(HttpStatus.OK, response.getStatusCode());
    //        assertNotNull(response.getBody());
    //        assertEquals(10,
    //                     response.getBody()
    //                             .getNumberOfAliveShips());
    //    }

    //    @Test
    //    void get_session_winner_should_return_winner_of_the_game() {
    //        //GET         /api/game/sessions/{sessionId}/winner
    //        var url_get_winner = "%s/api/game/sessions/{sessionId}/winner".formatted(baseUrl);
    //
    //        var session = createNewGameSession();
    //        var player_1 = createPlayerInSession(session, "Player_1");
    //        var player_2 = createPlayerInSession(session, "Player_2");
    //        createGameplay(session, player_1, player_2);
    //
    //        makeShotsToWinPlayer2(session, player_1, player_2);
    //
    //        var response = restTemplate.exchange(url_get_winner,
    //                                             HttpMethod.GET,
    //                                             HttpEntity.EMPTY,
    //                                             PlayerBaseInfoDto.class,
    //                                             session.getGameSessionId(),
    //                                             player_1.getPlayerId());
    //
    //        assertEquals(HttpStatus.OK, response.getStatusCode());
    //        assertNotNull(response.getBody());
    //
    //        var winner = response.getBody();
    //
    //        assertEquals("Player_2", winner.getPlayerName());
    //        assertTrue(winner.isReady());
    //        assertTrue(winner.isActive());
    //        assertTrue(winner.isWinner());
    //    }

    //    private void makeShotsToWinPlayer2(final GameSessionIdDto session, final PlayerDto player_1, final
    //    PlayerDto player_2) {
    //        var url_get_players_field = "%s/api/game/sessions/{sessionId}/players/{playerId}/field".formatted
    //        (baseUrl);
    //
    //        var responsePlayer1 = restTemplate.exchange(url_get_players_field,
    //                                                    HttpMethod.GET,
    //                                                    HttpEntity.EMPTY,
    //                                                    CellDto[][].class,
    //                                                    session.getGameSessionId(),
    //                                                    player_1.getPlayerId());
    //
    //        Set<Coordinate> coordinates = new HashSet<>();
    //        var player1Field = responsePlayer1.getBody();
    //
    //        assertNotNull(player1Field);
    //
    //        for (int i = 0; i < player1Field.length; i++) {
    //            var rowLength = player1Field[i].length;
    //            for (int j = 0; j < rowLength; j++) {
    //                var cell = player1Field[i][j];
    //                if (Objects.nonNull(cell.getShip()) && StringUtils.isNotBlank(cell.getShip()
    //                                                                                  .shipId())) {
    //                    var row = cell.getRow();
    //                    var col = cell.getCol();
    //                    coordinates.add(Coordinate.of(row, col));
    //                }
    //            }
    //        }
    //        var url_post_players_field_shot =
    //                "%s/api/game/sessions/{sessionId}/players/{playerId}/field?shot".formatted(baseUrl);
    //        restTemplate.exchange(url_post_players_field_shot,
    //                              HttpMethod.POST,
    //                              new HttpEntity<>(Coordinate.of(1, 0)),
    //                              ShotResultDto.class,
    //                              session.getGameSessionId(),
    //                              player_1.getPlayerId());
    //        coordinates.forEach(coordinate -> restTemplate.exchange(url_post_players_field_shot,
    //                                                                HttpMethod.POST,
    //                                                                new HttpEntity<>(coordinate),
    //                                                                ShotResultDto.class,
    //                                                                session.getGameSessionId(),
    //                                                                player_2.getPlayerId()));
    //    }

    //    private GameSessionIdDto createNewGameSession() {
    //        var url_post_player = "%s/api/game/sessions".formatted(baseUrl);
    //        var requestBody = new GameEditionDto(GameEdition.UKRAINIAN.name());
    //        return restTemplate.postForObject(url_post_player, requestBody, GameSessionIdDto.class);
    //    }

    //    private PlayerDto createPlayerInSession(GameSessionIdDto gameSessionIdDto, String playerName) {
    //        var url_post_player = "%s/api/game/sessions/{sessionId}/players".formatted(baseUrl);
    //        var playerRequestBody = new PlayerNameDto(playerName);
    //        return restTemplate.postForObject(url_post_player,
    //                                          playerRequestBody,
    //                                          PlayerDto.class,
    //                                          gameSessionIdDto.getGameSessionId());
    //    }

    //    private LinkedList<ShipDto> getAvailableShips(GameSessionIdDto gameSessionIdDto, PlayerDto playerDto) {
    //        //GET         /api/game/sessions/{sessionId}/players/{playerId}/ships?available
    //        var url_get_available_ships =
    //                "%s/api/game/sessions/{sessionId}/players/{playerId}/ships?available".formatted(baseUrl);
    //        var result = restTemplate.getForObject(url_get_available_ships,
    //                                               ShipDto[].class,
    //                                               gameSessionIdDto.getGameSessionId(),
    //                                               playerDto.getPlayerId());
    //        return new LinkedList<>(List.of(result));
    //    }

    //    private void createGameplay(GameSessionIdDto gameSessionIdDto, PlayerDto player1, PlayerDto player2) {
    //        addShipsForPlayer(gameSessionIdDto, player1);
    //        addShipsForPlayer(gameSessionIdDto, player2);
    //
    //        var url_post_players_start = "%s/api/game/sessions/{sessionId}/players/{playerId}?start".formatted
    //        (baseUrl);
    //
    //        restTemplate.postForObject(url_post_players_start,
    //                                   null,
    //                                   PlayerDto.class,
    //                                   gameSessionIdDto.getGameSessionId(),
    //                                   player1.getPlayerId());
    //
    //        restTemplate.postForObject(url_post_players_start,
    //                                   null,
    //                                   PlayerDto.class,
    //                                   gameSessionIdDto.getGameSessionId(),
    //                                   player2.getPlayerId());
    //
    //
    //    }

    //    private void addShipsForPlayer(GameSessionIdDto gameSessionIdDto, PlayerDto playerDto) {
    //        var url_post_add_ships =
    //                "%s/api/game/sessions/{sessionId}/players/{playerId}/ships/{shipId}".formatted(baseUrl);
    //        getCoordinates().forEach(coordinate -> {
    //            var playerShips = getAvailableShips(gameSessionIdDto, playerDto);
    //            var playerShipAddRequestBody =
    //                    new HttpEntity<>(new AddShipToFieldBody(coordinate, ShipDirection.HORIZONTAL.name()));
    //            var playerShipToAdd = playerShips.pop();
    //            restTemplate.exchange(url_post_add_ships,
    //                                  HttpMethod.PUT,
    //                                  playerShipAddRequestBody,
    //                                  ShipDto.class,
    //                                  gameSessionIdDto.getGameSessionId(),
    //                                  playerDto.getPlayerId(),
    //                                  playerShipToAdd.getShipId());
    //        });
    //    }

}
