package ua.kostenko.battleship.battleship.logic.api.impl;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import ua.kostenko.battleship.battleship.logic.api.IdGenerator;
import ua.kostenko.battleship.battleship.logic.engine.FieldManagement;
import ua.kostenko.battleship.battleship.logic.engine.FieldManagementImpl;
import ua.kostenko.battleship.battleship.logic.engine.Game;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
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
}