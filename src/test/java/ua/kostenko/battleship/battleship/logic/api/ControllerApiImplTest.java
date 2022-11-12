package ua.kostenko.battleship.battleship.logic.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;
import org.springframework.http.HttpStatus;
import ua.kostenko.battleship.battleship.logic.api.dtos.CellDto;
import ua.kostenko.battleship.battleship.logic.api.dtos.ShipDto;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameInternalProblemException;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameStageIsNotCorrectException;
import ua.kostenko.battleship.battleship.logic.engine.GameImplTest;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipType;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.utils.FieldUtils;
import ua.kostenko.battleship.battleship.logic.persistence.InMemoryPersistence;
import ua.kostenko.battleship.battleship.logic.persistence.Persistence;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ControllerApiImplTest {

    private static final String playerName1 = "player_name_1";
    private static final String playerName2 = "player_name_2";
    private Persistence persistence;
    private ControllerApi controller;
    private String emptySessionId;
    private String sessionIdWithPlayers;
    private String sessionIdWithPlayersPlayer1ID;
    private String sessionIdWithPlayersPlayer2ID;

    @BeforeEach
    void before() {
        persistence = new InMemoryPersistence();
        controller = new ControllerApiImpl(persistence);

        var result = controller.createGameSession(GameEdition.UKRAINIAN.name());
        assert Objects.nonNull(result.getBody());
        emptySessionId = result.getBody();

        var withPlayers = controller.createGameSession(GameEdition.UKRAINIAN.name());
        assert Objects.nonNull(withPlayers.getBody());
        sessionIdWithPlayers = withPlayers.getBody();

        var player1 = controller.createPlayerInSession(sessionIdWithPlayers, playerName1);
        assert Objects.nonNull(player1.getBody());
        sessionIdWithPlayersPlayer1ID = player1.getBody()
                                               .getPlayerId();

        var player2 = controller.createPlayerInSession(sessionIdWithPlayers, playerName2);
        assert Objects.nonNull(player2.getBody());
        sessionIdWithPlayersPlayer2ID = player2.getBody()
                                               .getPlayerId();
    }

    @Test
    void testGetGameEditions() {
        var gameEditionsResponse = controller.getGameEditions();
        assertEquals(HttpStatus.OK, gameEditionsResponse.getStatusCode());
        assertNotNull(gameEditionsResponse.getBody());
        assertEquals(2,
                     gameEditionsResponse.getBody()
                                         .size());

        var expected = Set.of(GameEdition.UKRAINIAN, GameEdition.MILTON_BRADLEY);
        assertTrue(expected.containsAll(gameEditionsResponse.getBody()));
    }

    @Test
    void testCreateGameSession() {
        var createGameSessionResponse = controller.createGameSession(GameEdition.UKRAINIAN.name());
        assertEquals(HttpStatus.CREATED, createGameSessionResponse.getStatusCode());
        assertNotNull(createGameSessionResponse.getBody());
        assertTrue(StringUtils.isNotBlank(createGameSessionResponse.getBody()));

        var game = persistence.load(createGameSessionResponse.getBody());
        assertTrue(game.isPresent());
    }

    @Test
    void testCreatePlayerInSession() {
        var createPlayerResponse = controller.createPlayerInSession(emptySessionId, "player_1");

        assertEquals(HttpStatus.CREATED, createPlayerResponse.getStatusCode());
        assertNotNull(createPlayerResponse.getBody());
        assertFalse(createPlayerResponse.getBody()
                                        .isActive());
        assertFalse(createPlayerResponse.getBody()
                                        .isReady());
        assertFalse(createPlayerResponse.getBody()
                                        .isWinner());
        assertEquals("player_1",
                     createPlayerResponse.getBody()
                                         .getPlayerName());

        var game = persistence.load(emptySessionId);
        assertTrue(game.isPresent());
        assertNotNull(game.get()
                          .getPlayer(createPlayerResponse.getBody()
                                                         .getPlayerId()));

        controller.createPlayerInSession(emptySessionId, "player_2");

        assertThrows(GameInternalProblemException.class,
                     () -> controller.createPlayerInSession(emptySessionId, "player_3"));
    }

    @Test
    void testGetPlayer() {
        var getPlayerResponse = controller.getPlayer(sessionIdWithPlayers, sessionIdWithPlayersPlayer1ID);

        assertEquals(HttpStatus.OK, getPlayerResponse.getStatusCode());
        assertNotNull(getPlayerResponse.getBody());
        assertEquals(sessionIdWithPlayersPlayer1ID,
                     getPlayerResponse.getBody()
                                      .getPlayerId());

        assertThrows(GameInternalProblemException.class, () -> controller.getPlayer(emptySessionId, "player_3"));
    }

    @Test
    void testStartGame() {
        var game = persistence.load(sessionIdWithPlayers);
        assert game.isPresent();
        GameImplTest.addShipsToField(game.get()
                                         .getPlayer(sessionIdWithPlayersPlayer1ID));

        assertThrows(GameInternalProblemException.class, () -> controller.startGame(emptySessionId, "player"));

        var startGameResponse = controller.startGame(sessionIdWithPlayers, sessionIdWithPlayersPlayer1ID);

        assertEquals(HttpStatus.OK, startGameResponse.getStatusCode());
        assertNotNull(startGameResponse.getBody());
        assertEquals(sessionIdWithPlayersPlayer1ID,
                     startGameResponse.getBody()
                                      .getPlayerId());
        assertTrue(startGameResponse.getBody()
                                    .isReady());
    }

    @Test
    void testGetOpponent() {
        controller.createPlayerInSession(emptySessionId, "player_1");
        assertThrows(GameInternalProblemException.class, () -> controller.startGame(emptySessionId, "player_1"));

        var getOpponentResponse = controller.getOpponent(sessionIdWithPlayers, sessionIdWithPlayersPlayer1ID);

        assertEquals(HttpStatus.OK, getOpponentResponse.getStatusCode());
        assertNotNull(getOpponentResponse.getBody());
        assertEquals(playerName2,
                     getOpponentResponse.getBody()
                                        .getPlayerName());
        assertFalse(getOpponentResponse.getBody()
                                       .isReady());
        assertFalse(getOpponentResponse.getBody()
                                       .isWinner());
        assertFalse(getOpponentResponse.getBody()
                                       .isActive());
    }

    @Test
    void testGetField() {
        var game = persistence.load(sessionIdWithPlayers);
        assert game.isPresent();
        GameImplTest.addShipsToField(game.get()
                                         .getPlayer(sessionIdWithPlayersPlayer1ID));

        var getFieldResponse = controller.getField(sessionIdWithPlayers, sessionIdWithPlayersPlayer1ID);
        assertEquals(HttpStatus.OK, getFieldResponse.getStatusCode());
        assertNotNull(getFieldResponse.getBody());
        assertEquals(10, getFieldResponse.getBody().length);

        Set<CellDto> cells = new HashSet<>();
        for (int i = 0; i < getFieldResponse.getBody().length; i++) {
            assertEquals(10, getFieldResponse.getBody()[i].length);
            for (int j = 0; j < getFieldResponse.getBody()[i].length; j++) {
                var cell = getFieldResponse.getBody()[i][j];
                cells.add(cell);

                assertEquals(i, cell.getRow());
                assertEquals(j, cell.getCol());
            }
        }
        assertTrue(cells.stream()
                        .anyMatch(c -> Objects.nonNull(c.getShip())));

        assertThrows(GameInternalProblemException.class,
                     () -> controller.getField(sessionIdWithPlayers, "non_existing"));
    }

    @Test
    void testGetFieldOfOpponent() {
        var game = persistence.load(sessionIdWithPlayers);
        assert game.isPresent();
        GameImplTest.addShipsToField(game.get()
                                         .getPlayer(sessionIdWithPlayersPlayer1ID));

        var getOpponentFieldResponse =
                controller.getFieldOfOpponent(sessionIdWithPlayers, sessionIdWithPlayersPlayer2ID);
        assertEquals(HttpStatus.OK, getOpponentFieldResponse.getStatusCode());
        assertNotNull(getOpponentFieldResponse.getBody());
        assertEquals(10, getOpponentFieldResponse.getBody().length);

        Set<CellDto> cells = new HashSet<>();
        for (int i = 0; i < getOpponentFieldResponse.getBody().length; i++) {
            assertEquals(10, getOpponentFieldResponse.getBody()[i].length);
            for (int j = 0; j < getOpponentFieldResponse.getBody()[i].length; j++) {
                var cell = getOpponentFieldResponse.getBody()[i][j];
                cells.add(cell);

                assertEquals(i, cell.getRow());
                assertEquals(j, cell.getCol());
            }
        }
        assertFalse(cells.stream()
                         .anyMatch(c -> Objects.nonNull(c.getShip())));

        assertThrows(GameInternalProblemException.class,
                     () -> controller.getFieldOfOpponent(sessionIdWithPlayers, "non_existing"));
    }

    @Test
    void testGetPrepareShipsList() {
        var getPrepareShipsListResponse =
                controller.getPrepareShipsList(sessionIdWithPlayers, sessionIdWithPlayersPlayer1ID);
        assertEquals(HttpStatus.OK, getPrepareShipsListResponse.getStatusCode());
        assertNotNull(getPrepareShipsListResponse.getBody());
        assertEquals(10,
                     getPrepareShipsListResponse.getBody()
                                                .size());

        assertThrows(GameInternalProblemException.class, () -> controller.getField(emptySessionId, "non_existing"));
    }

    @Test
    void testAddShipToField() {
        var getPrepareShipsListResponse =
                controller.getPrepareShipsList(sessionIdWithPlayers, sessionIdWithPlayersPlayer1ID);
        assertEquals(HttpStatus.OK, getPrepareShipsListResponse.getStatusCode());
        assertNotNull(getPrepareShipsListResponse.getBody());

        var shipId = getPrepareShipsListResponse.getBody()
                                                .stream()
                                                .findAny()
                                                .map(ShipDto::getShipId);
        assert shipId.isPresent();

        var addShipToFieldResponse = controller.addShipToField(sessionIdWithPlayers,
                                                               sessionIdWithPlayersPlayer1ID,
                                                               shipId.get(),
                                                               Coordinate.of(5, 5),
                                                               "VERTICAL");

        assertEquals(HttpStatus.OK, addShipToFieldResponse.getStatusCode());
        assertNotNull(addShipToFieldResponse.getBody());
        assertEquals(shipId.get(),
                     addShipToFieldResponse.getBody()
                                           .getShipId());

        assertThrows(GameInternalProblemException.class,
                     () -> controller.addShipToField(sessionIdWithPlayers,
                                                     sessionIdWithPlayersPlayer1ID,
                                                     "non_existing",
                                                     Coordinate.of(5, 5),
                                                     "VERTICAL"));
    }

    @Test
    void testRemoveShipFromField() {
        assertThrows(GameInternalProblemException.class,
                     () -> controller.removeShipFromField(emptySessionId, "non_existing", Coordinate.of(0, 0)));

        var getPrepareShipsListResponse =
                controller.getPrepareShipsList(sessionIdWithPlayers, sessionIdWithPlayersPlayer1ID);
        assertEquals(HttpStatus.OK, getPrepareShipsListResponse.getStatusCode());
        assertNotNull(getPrepareShipsListResponse.getBody());

        var shipId = getPrepareShipsListResponse.getBody()
                                                .stream()
                                                .findAny()
                                                .map(ShipDto::getShipId);
        assert shipId.isPresent();

        var addShipToFieldResponse = controller.addShipToField(sessionIdWithPlayers,
                                                               sessionIdWithPlayersPlayer1ID,
                                                               shipId.get(),
                                                               Coordinate.of(5, 5),
                                                               "VERTICAL");

        assertEquals(HttpStatus.OK, addShipToFieldResponse.getStatusCode());
        assertNotNull(addShipToFieldResponse.getBody());
        assertEquals(shipId.get(),
                     addShipToFieldResponse.getBody()
                                           .getShipId());

        var removeShipFromFieldResponse = controller.removeShipFromField(sessionIdWithPlayers,
                                                                         sessionIdWithPlayersPlayer1ID,
                                                                         Coordinate.of(5, 5));

        assertEquals(HttpStatus.OK, removeShipFromFieldResponse.getStatusCode());
        assertNotNull(removeShipFromFieldResponse.getBody());
        assertEquals(shipId.get(), removeShipFromFieldResponse.getBody());
    }

    @Test
    void testGetActivePlayer() {
        assertThrows(GameStageIsNotCorrectException.class, () -> controller.getActivePlayer(emptySessionId));

        var game = persistence.load(sessionIdWithPlayers);

        assert game.isPresent();

        GameImplTest.addShipsToField(game.get()
                                         .getPlayer(sessionIdWithPlayersPlayer1ID));
        GameImplTest.addShipsToField(game.get()
                                         .getPlayer(sessionIdWithPlayersPlayer2ID));
        controller.startGame(sessionIdWithPlayers, sessionIdWithPlayersPlayer1ID);
        controller.startGame(sessionIdWithPlayers, sessionIdWithPlayersPlayer2ID);

        var getActivePlayerResponse = controller.getActivePlayer(sessionIdWithPlayers);

        assertEquals(HttpStatus.OK, getActivePlayerResponse.getStatusCode());
        assertNotNull(getActivePlayerResponse.getBody());
        assertEquals(playerName1,
                     getActivePlayerResponse.getBody()
                                            .getPlayerName());
    }

    @Test
    void testMakeShot() {
        assertThrows(GameInternalProblemException.class,
                     () -> controller.makeShot(sessionIdWithPlayers,
                                               sessionIdWithPlayersPlayer1ID,
                                               Coordinate.of(0, 0)));

        var game = persistence.load(sessionIdWithPlayers);

        assert game.isPresent();

        GameImplTest.addShipsToField(game.get()
                                         .getPlayer(sessionIdWithPlayersPlayer1ID));
        GameImplTest.addShipsToField(game.get()
                                         .getPlayer(sessionIdWithPlayersPlayer2ID));
        controller.startGame(sessionIdWithPlayers, sessionIdWithPlayersPlayer1ID);
        controller.startGame(sessionIdWithPlayers, sessionIdWithPlayersPlayer2ID);

        var makeShotResponse =
                controller.makeShot(sessionIdWithPlayers, sessionIdWithPlayersPlayer1ID, Coordinate.of(0, 0));

        assertEquals(HttpStatus.OK, makeShotResponse.getStatusCode());
        assertNotNull(makeShotResponse.getBody());
        assertTrue(Set.of(ShotResult.HIT, ShotResult.DESTROYED)
                      .contains(makeShotResponse.getBody()));
    }

    @Test
    void testGetNumberOfUndamagedCells() {
        var game = persistence.load(sessionIdWithPlayers);

        assert game.isPresent();

        GameImplTest.addShipsToField(game.get()
                                         .getPlayer(sessionIdWithPlayersPlayer1ID));
        GameImplTest.addShipsToField(game.get()
                                         .getPlayer(sessionIdWithPlayersPlayer2ID));
        controller.startGame(sessionIdWithPlayers, sessionIdWithPlayersPlayer1ID);
        controller.startGame(sessionIdWithPlayers, sessionIdWithPlayersPlayer2ID);

        var getNumberOfUndamagedCellsResponse =
                controller.getNumberOfUndamagedCells(sessionIdWithPlayers, sessionIdWithPlayersPlayer2ID);
        assertEquals(HttpStatus.OK, getNumberOfUndamagedCellsResponse.getStatusCode());
        assertNotNull(getNumberOfUndamagedCellsResponse.getBody());
        assertEquals(100, getNumberOfUndamagedCellsResponse.getBody());

        var player2ShipCoordinates = FieldUtils.convertToFlatSet(game.get()
                                                                     .getPlayer(sessionIdWithPlayersPlayer2ID)
                                                                     .getField()
                                                                     .getField())
                                               .stream()
                                               .filter(Cell::hasShip)
                                               .filter(s -> ShipType.DESTROYER == s.ship()
                                                                                   .shipType())
                                               .map(Cell::coordinate)
                                               .findAny();

        assert player2ShipCoordinates.isPresent();

        controller.makeShot(sessionIdWithPlayers, sessionIdWithPlayersPlayer1ID, player2ShipCoordinates.get());

        getNumberOfUndamagedCellsResponse =
                controller.getNumberOfUndamagedCells(sessionIdWithPlayers, sessionIdWithPlayersPlayer2ID);
        assertEquals(HttpStatus.OK, getNumberOfUndamagedCellsResponse.getStatusCode());
        assertNotNull(getNumberOfUndamagedCellsResponse.getBody());
        assertEquals(99, getNumberOfUndamagedCellsResponse.getBody());
    }

    @Test
    void testGetNumberOfNotDestroyedShips() {
        var game = persistence.load(sessionIdWithPlayers);

        assert game.isPresent();

        GameImplTest.addShipsToField(game.get()
                                         .getPlayer(sessionIdWithPlayersPlayer1ID));
        GameImplTest.addShipsToField(game.get()
                                         .getPlayer(sessionIdWithPlayersPlayer2ID));
        controller.startGame(sessionIdWithPlayers, sessionIdWithPlayersPlayer1ID);
        controller.startGame(sessionIdWithPlayers, sessionIdWithPlayersPlayer2ID);

        var getNumberOfNotDestroyedShipsResponse =
                controller.getNumberOfNotDestroyedShips(sessionIdWithPlayers, sessionIdWithPlayersPlayer2ID);
        assertEquals(HttpStatus.OK, getNumberOfNotDestroyedShipsResponse.getStatusCode());
        assertNotNull(getNumberOfNotDestroyedShipsResponse.getBody());
        assertEquals(10, getNumberOfNotDestroyedShipsResponse.getBody());

        var player2ShipCoordinates = FieldUtils.convertToFlatSet(game.get()
                                                                     .getPlayer(sessionIdWithPlayersPlayer2ID)
                                                                     .getField()
                                                                     .getField())
                                               .stream()
                                               .filter(Cell::hasShip)
                                               .filter(s -> s.ship()
                                                             .shipSize() == 1)
                                               .map(Cell::coordinate)
                                               .findAny();

        assert player2ShipCoordinates.isPresent();

        controller.makeShot(sessionIdWithPlayers, sessionIdWithPlayersPlayer1ID, player2ShipCoordinates.get());

        getNumberOfNotDestroyedShipsResponse =
                controller.getNumberOfNotDestroyedShips(sessionIdWithPlayers, sessionIdWithPlayersPlayer2ID);
        assertEquals(HttpStatus.OK, getNumberOfNotDestroyedShipsResponse.getStatusCode());
        assertNotNull(getNumberOfNotDestroyedShipsResponse.getBody());
        assertEquals(9, getNumberOfNotDestroyedShipsResponse.getBody());
    }

    @Test
    void testGetWinner() {
        var game = persistence.load(sessionIdWithPlayers);

        assert game.isPresent();

        GameImplTest.addShipsToField(game.get()
                                         .getPlayer(sessionIdWithPlayersPlayer1ID));
        GameImplTest.addShipsToField(game.get()
                                         .getPlayer(sessionIdWithPlayersPlayer2ID));
        controller.startGame(sessionIdWithPlayers, sessionIdWithPlayersPlayer1ID);
        controller.startGame(sessionIdWithPlayers, sessionIdWithPlayersPlayer2ID);

        var getNumberOfNotDestroyedShipsResponse =
                controller.getNumberOfNotDestroyedShips(sessionIdWithPlayers, sessionIdWithPlayersPlayer2ID);
        assertEquals(HttpStatus.OK, getNumberOfNotDestroyedShipsResponse.getStatusCode());
        assertNotNull(getNumberOfNotDestroyedShipsResponse.getBody());
        assertEquals(10, getNumberOfNotDestroyedShipsResponse.getBody());

        var coordinatesOfOpponentShips = FieldUtils.convertToFlatSet(game.get()
                                                                         .getPlayer(sessionIdWithPlayersPlayer2ID)
                                                                         .getField()
                                                                         .getField())
                                                   .stream()
                                                   .filter(Cell::hasShip)
                                                   .map(Cell::coordinate)
                                                   .collect(Collectors.toSet());

        coordinatesOfOpponentShips.forEach(c -> controller.makeShot(sessionIdWithPlayers,
                                                                    sessionIdWithPlayersPlayer1ID,
                                                                    c));

        getNumberOfNotDestroyedShipsResponse =
                controller.getNumberOfNotDestroyedShips(sessionIdWithPlayers, sessionIdWithPlayersPlayer2ID);
        assertEquals(HttpStatus.OK, getNumberOfNotDestroyedShipsResponse.getStatusCode());
        assertNotNull(getNumberOfNotDestroyedShipsResponse.getBody());
        assertEquals(0, getNumberOfNotDestroyedShipsResponse.getBody());

        var getWinnerResponse = controller.getWinner(sessionIdWithPlayers);
        assertEquals(HttpStatus.OK, getWinnerResponse.getStatusCode());
        assertNotNull(getWinnerResponse.getBody());
        assertTrue(getWinnerResponse.getBody()
                                    .isWinner());
        assertTrue(getWinnerResponse.getBody()
                                    .isActive());
        assertTrue(getWinnerResponse.getBody()
                                    .isReady());
        assertEquals(playerName1,
                     getWinnerResponse.getBody()
                                      .getPlayerName());
    }
}
