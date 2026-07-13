package ua.kostenko.battleship.battleship.logic.api.impl;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import ua.kostenko.battleship.battleship.logic.api.IdGenerator;
import ua.kostenko.battleship.battleship.logic.api.exceptions.*;
import ua.kostenko.battleship.battleship.logic.engine.FieldManagement;
import ua.kostenko.battleship.battleship.logic.engine.FieldManagementImpl;
import ua.kostenko.battleship.battleship.logic.engine.Game;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.exceptions.CellAlreadyShotException;
import ua.kostenko.battleship.battleship.logic.engine.exceptions.SessionFullException;
import ua.kostenko.battleship.battleship.logic.engine.exceptions.ShipNotAvailableForAddException;
import ua.kostenko.battleship.battleship.logic.engine.exceptions.ShipsNotAllPlacedException;
import ua.kostenko.battleship.battleship.logic.engine.models.GameplayState;
import ua.kostenko.battleship.battleship.logic.engine.models.OpponentInfo;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipDirection;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipType;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.models.records.GameState;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;
import ua.kostenko.battleship.battleship.logic.persistence.Persistence;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameControllerApiImplTest {
    @Mock
    Persistence persistence;
    @Mock
    IdGenerator idGenerator;
    @Mock
    Logger log;
    @InjectMocks
    GameControllerApiImpl gameControllerApiImpl;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAvailableGameEditions() {
        List<GameEdition> result = gameControllerApiImpl.getAvailableGameEditions();
        assertEquals(List.of(GameEdition.UKRAINIAN, GameEdition.MILTON_BRADLEY), result);
    }

    @Test
    void testCreateGameSession() {
        when(persistence.save(any())).thenReturn(null);
        when(idGenerator.generateId()).thenReturn("generatedId");

        String result = gameControllerApiImpl.createGameSession(GameEdition.UKRAINIAN.name());
        assertEquals("generatedId", result);
    }

    @Test
    void testCreatePlayerInSession() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.INITIALIZED);
        var game = Game.fromGameState(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(game));
        when(persistence.save(any())).thenReturn(null);
        when(idGenerator.generateId()).thenReturn("playerId");

        Player result = gameControllerApiImpl.createPlayerInSession("sessionId", "playerName");

        assertNotNull(result);
        assertFalse(result.isReady());
        assertFalse(result.isWinner());
        assertFalse(result.isActive());

        assertEquals("playerId", result.getPlayerId());
        assertEquals("playerName", result.getPlayerName());
    }

    @Test
    void testGetCurrentGameStage() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.INITIALIZED);
        var game = Game.fromGameState(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(game));

        GameStage result = gameControllerApiImpl.getCurrentGameStage("sessionId");

        assertEquals(gameState.gameStage(), result);
    }

    @Test
    void testGetLastSessionChangeTime() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.INITIALIZED);
        var game = Game.fromGameState(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(game));


        String result = gameControllerApiImpl.getLastSessionChangeTime("sessionId");
        assertEquals(gameState.lastUpdate(), result);
    }

    @Test
    void testGetShipsNotOnTheBoard() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.INITIALIZED);
        var mockGame = Mockito.mock(Game.class);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(mockGame.getShipsNotOnTheField(anyString())).thenReturn(Set.of(new Ship("shipId",
                ShipType.PATROL_BOAT,
                ShipDirection.HORIZONTAL,
                0)));
        when(mockGame.getGameState()).thenReturn(gameState);

        List<Ship> result = gameControllerApiImpl.getShipsNotOnTheBoard("sessionId", "playerId");
        assertEquals(1, result.size());
        assertEquals(1, result.size());
    }

    @Test
    void testAddShipToField() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.INITIALIZED);
        var mockGame = Mockito.mock(Game.class);
        var ship = Ship.builder()
                .shipId("shipId")
                .shipDirection(ShipDirection.HORIZONTAL)
                .shipSize(4)
                .shipType(ShipType.SUBMARINE)
                .build();

        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(persistence.save(any())).thenReturn(null);
        when(mockGame.getAllShips(anyString())).thenReturn(Set.of(ship));
        when(mockGame.getGameState()).thenReturn(gameState);

        Ship result = gameControllerApiImpl.addShipToField("sessionId",
                "playerId",
                "shipId",
                new Coordinate(0, 0),
                ShipDirection.HORIZONTAL.name());

        verify(mockGame, atLeastOnce()).addShipToField(anyString(), any(Coordinate.class), any(Ship.class));
    }

    @Test
    void testRemoveShipFromField() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.INITIALIZED);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(persistence.save(any())).thenReturn(null);

        when(mockGame.removeShipFromField(anyString(), any(Coordinate.class))).thenReturn(Optional.of("testSuccess"));

        String result = gameControllerApiImpl.removeShipFromField("sessionId", "playerId", new Coordinate(0, 0));
        assertEquals("testSuccess", result);
    }

    @Test
    void testGetOpponentInformation() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.INITIALIZED);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(persistence.save(any())).thenReturn(null);

        Player player = Player.builder()
                .playerId("id")
                .playerName("name")
                .fieldManagement(new FieldManagementImpl())
                .shipsNotOnTheField(new HashSet<>())
                .allPlayerShips(new HashSet<>())
                .build();
        when(mockGame.getOpponent(anyString())).thenReturn(player);

        OpponentInfo result = gameControllerApiImpl.getOpponentInformation("sessionId", "playerId");
        assertEquals("name", result.playerName());
    }

    @Test
    void testGetPreparationField() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.PREPARATION);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(persistence.save(any())).thenReturn(null);
        when(mockGame.getField(anyString())).thenReturn(new Cell[10][10]);

        Cell[][] result = gameControllerApiImpl.getPreparationField("sessionId", "playerId");
        assertNotNull(result);
    }

    @Test
    void testStartGame() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.INITIALIZED);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(persistence.save(any())).thenReturn(null);

        Player result = gameControllerApiImpl.startGame("sessionId", "playerId");

        verify(mockGame, atLeastOnce()).changePlayerStatusToReady(anyString());
        verify(mockGame, atLeastOnce()).getPlayer(anyString());
    }

    @Test
    void testGetGameState() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.IN_GAME);
        var mockGame = Mockito.mock(Game.class);
        var mockPlayer1 = Mockito.mock(Player.class);
        var mockPlayer2 = Mockito.mock(Player.class);
        var mockFieldManagement1 = Mockito.mock(FieldManagement.class);
        var mockFieldManagement2 = Mockito.mock(FieldManagement.class);

        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(persistence.save(any())).thenReturn(null);
        when(mockGame.getPlayer(anyString())).thenReturn(mockPlayer1);
        when(mockGame.getOpponent(anyString())).thenReturn(mockPlayer2);
        when(mockPlayer1.getFieldManagement()).thenReturn(mockFieldManagement1);
        when(mockPlayer2.getFieldManagement()).thenReturn(mockFieldManagement2);
        when(mockPlayer1.isWinner()).thenReturn(false);
        when(mockPlayer2.isWinner()).thenReturn(false);

        GameplayState result = gameControllerApiImpl.getGameState("sessionId", "playerId");

        verify(mockGame, atLeastOnce()).getGameState();
        verify(mockGame, atLeastOnce()).getPlayer(anyString());
        verify(mockGame, atLeastOnce()).getOpponent(anyString());
        verify(mockPlayer1, atMostOnce()).getFieldManagement();
        verify(mockPlayer2, atMostOnce()).getFieldManagement();
        verify(mockFieldManagement1, atMostOnce()).getField();
        verify(mockFieldManagement2, atMostOnce()).getFieldWithHiddenShips();
    }

    @Test
    void testMakeShotByField() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.IN_GAME);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(persistence.save(any())).thenReturn(null);
        when(mockGame.makeShot(anyString(), any(Coordinate.class))).thenReturn(ShotResult.MISS);

        ShotResult result = gameControllerApiImpl.makeShotByField("sessionId", "playerId", new Coordinate(0, 0));

        verify(mockGame, atMostOnce()).makeShot(anyString(), any(Coordinate.class));
        assertEquals(ShotResult.MISS, result);
    }

    @Test
    void testMakeShotByField_cellAlreadyShot_throwsGameCellAlreadyShotException() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.IN_GAME);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(mockGame.makeShot(anyString(), any(Coordinate.class))).thenThrow(new CellAlreadyShotException(
                "Cell has already been shot."));

        assertThrows(GameCellAlreadyShotException.class,
                () -> gameControllerApiImpl.makeShotByField("sessionId", "playerId", new Coordinate(0, 0)));

        verify(mockGame, atMostOnce()).makeShot(anyString(), any(Coordinate.class));
        verify(persistence, never()).save(any());
    }

    // ---- Ticket A: confirmed production-bug regression tests ----

    @Test
    void testGetOpponentInformation_soloPlayer_throwsGameOpponentNotFoundException() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.WAITING_FOR_PLAYERS);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(mockGame.getOpponent(anyString())).thenThrow(new IllegalArgumentException(
                "Player with provided filter is not found"));

        assertThrows(GameOpponentNotFoundException.class,
                () -> gameControllerApiImpl.getOpponentInformation("sessionId", "playerId"));
    }

    @Test
    void testGetGameState_playerNotFound_throwsGamePlayerNotFoundException() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.IN_GAME);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(mockGame.getPlayer(anyString())).thenThrow(new IllegalArgumentException(
                "Player with id playerId not found"));

        assertThrows(GamePlayerNotFoundException.class,
                () -> gameControllerApiImpl.getGameState("sessionId", "playerId"));
    }

    @Test
    void testGetGameState_soloPlayer_throwsGameOpponentNotFoundException() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.WAITING_FOR_PLAYERS);
        var mockGame = Mockito.mock(Game.class);
        var mockPlayer1 = Mockito.mock(Player.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(mockGame.getPlayer(anyString())).thenReturn(mockPlayer1);
        when(mockGame.getOpponent(anyString())).thenThrow(new IllegalArgumentException(
                "Player with provided filter is not found"));

        assertThrows(GameOpponentNotFoundException.class,
                () -> gameControllerApiImpl.getGameState("sessionId", "playerId"));
    }

    // ---- Ticket B: remaining gap-fill + correctness-bug regression tests ----

    @Test
    void testCreatePlayerInSession_sessionFull_throwsGameSessionFullException() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.WAITING_FOR_PLAYERS);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(idGenerator.generateId()).thenReturn("playerId");
        when(mockGame.createPlayer(anyString(), anyString())).thenThrow(new SessionFullException(
                "Game can't have more than 2 players"));

        assertThrows(GameSessionFullException.class,
                () -> gameControllerApiImpl.createPlayerInSession("sessionId", "playerName"));

        verify(persistence, never()).save(any());
    }

    @Test
    void testCreatePlayerInSession_wrongStage_throwsGameStageIsNotCorrectException() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.PREPARATION);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(idGenerator.generateId()).thenReturn("playerId");
        when(mockGame.createPlayer(anyString(), anyString())).thenThrow(new IllegalStateException(
                "PREPARATION doesn't allow operation. There is no possibility to create player now"));

        assertThrows(GameStageIsNotCorrectException.class,
                () -> gameControllerApiImpl.createPlayerInSession("sessionId", "playerName"));
    }

    @Test
    void testStartGame_shipsNotAllPlaced_throwsGameShipsNotAllPlacedException() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.PREPARATION);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        doThrow(new ShipsNotAllPlacedException(
                "Player can't be made ready. Player has ships not added to the field")).when(mockGame)
                .changePlayerStatusToReady(
                        anyString());

        assertThrows(GameShipsNotAllPlacedException.class,
                () -> gameControllerApiImpl.startGame("sessionId", "playerId"));

        verify(persistence, never()).save(any());
    }

    @Test
    void testStartGame_wrongStage_throwsGameStageIsNotCorrectException() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.INITIALIZED);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        doThrow(new IllegalStateException(
                "INITIALIZED doesn't allow operation. State can be changed to ready only in Preparation stage")).when(
                mockGame).changePlayerStatusToReady(anyString());

        assertThrows(GameStageIsNotCorrectException.class,
                () -> gameControllerApiImpl.startGame("sessionId", "playerId"));
    }

    @Test
    void testAddShipToField_shipAlreadyPlaced_throwsGameShipAlreadyPlacedException() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.PREPARATION);
        var mockGame = Mockito.mock(Game.class);
        var ship = Ship.builder()
                .shipId("shipId")
                .shipDirection(ShipDirection.HORIZONTAL)
                .shipSize(4)
                .shipType(ShipType.SUBMARINE)
                .build();
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(mockGame.getAllShips(anyString())).thenReturn(Set.of(ship));
        doThrow(new ShipNotAvailableForAddException(
                "Ship %s is not available for add operation".formatted(ship))).when(mockGame)
                .addShipToField(anyString(),
                        any(Coordinate.class),
                        any(Ship.class));

        assertThrows(GameShipAlreadyPlacedException.class,
                () -> gameControllerApiImpl.addShipToField("sessionId",
                        "playerId",
                        "shipId",
                        new Coordinate(0, 0),
                        ShipDirection.HORIZONTAL.name()));
    }

    @Test
    void testAddShipToField_wrongStage_throwsGameStageIsNotCorrectException() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.INITIALIZED);
        var mockGame = Mockito.mock(Game.class);
        var ship = Ship.builder()
                .shipId("shipId")
                .shipDirection(ShipDirection.HORIZONTAL)
                .shipSize(4)
                .shipType(ShipType.SUBMARINE)
                .build();
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(mockGame.getAllShips(anyString())).thenReturn(Set.of(ship));
        doThrow(new IllegalStateException("INITIALIZED doesn't allow operation. Ship can be added only in "
                + "Preparation stage")).when(mockGame)
                .addShipToField(anyString(),
                        any(Coordinate.class),
                        any(Ship.class));

        assertThrows(GameStageIsNotCorrectException.class,
                () -> gameControllerApiImpl.addShipToField("sessionId",
                        "playerId",
                        "shipId",
                        new Coordinate(0, 0),
                        ShipDirection.HORIZONTAL.name()));
    }

    /**
     * Regression test for the catch-ordering correctness bug: {@link GameShipIdIsNotCorrectException} extends
     * {@link IllegalArgumentException}, so it must be rethrown as-is rather than demoted to a generic
     * {@link ua.kostenko.battleship.battleship.logic.api.exceptions.GameInternalProblemException}/500 by the
     * broad catch-all.
     */
    @Test
    void testAddShipToField_invalidShipId_throwsGameShipIdIsNotCorrectException() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.PREPARATION);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(mockGame.getAllShips(anyString())).thenReturn(Set.of());

        assertThrows(GameShipIdIsNotCorrectException.class,
                () -> gameControllerApiImpl.addShipToField("sessionId",
                        "playerId",
                        "unknown-ship",
                        new Coordinate(0, 0),
                        ShipDirection.HORIZONTAL.name()));

        verify(persistence, never()).save(any());
    }

    @Test
    void testRemoveShipFromField_wrongStage_throwsGameStageIsNotCorrectException() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.INITIALIZED);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(mockGame.removeShipFromField(anyString(), any(Coordinate.class))).thenThrow(new IllegalStateException(
                "INITIALIZED doesn't allow operation. Ship can be removed only in Preparation stage"));

        assertThrows(GameStageIsNotCorrectException.class,
                () -> gameControllerApiImpl.removeShipFromField("sessionId", "playerId", new Coordinate(0, 0)));
    }

    @Test
    void testMakeShotByField_wrongStage_throwsGameStageIsNotCorrectException() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.PREPARATION);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(mockGame.makeShot(anyString(), any(Coordinate.class))).thenThrow(new IllegalStateException(
                "PREPARATION doesn't allow operation. Shot can be made only in IN_GAME stage"));

        assertThrows(GameStageIsNotCorrectException.class,
                () -> gameControllerApiImpl.makeShotByField("sessionId", "playerId", new Coordinate(0, 0)));

        verify(persistence, never()).save(any());
    }

    @Test
    void testGetShipsNotOnTheBoard_playerNotFound_throwsGamePlayerNotFoundException() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.PREPARATION);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(mockGame.getShipsNotOnTheField(anyString())).thenThrow(new IllegalArgumentException(
                "Player with id playerId not found"));

        assertThrows(GamePlayerNotFoundException.class,
                () -> gameControllerApiImpl.getShipsNotOnTheBoard("sessionId", "playerId"));
    }

    @Test
    void testGetPreparationField_playerNotFound_throwsGamePlayerNotFoundException() {
        var gameState = GameState.create(GameEdition.UKRAINIAN, "sessionId", GameStage.PREPARATION);
        var mockGame = Mockito.mock(Game.class);
        when(mockGame.getGameState()).thenReturn(gameState);
        when(persistence.load(anyString())).thenReturn(Optional.of(mockGame));
        when(mockGame.getField(anyString())).thenThrow(new IllegalArgumentException(
                "Player with id playerId not found"));

        assertThrows(GamePlayerNotFoundException.class,
                () -> gameControllerApiImpl.getPreparationField("sessionId", "playerId"));
    }
}