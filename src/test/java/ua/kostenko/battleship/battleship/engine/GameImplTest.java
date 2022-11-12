package ua.kostenko.battleship.battleship.engine;

import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.battleship.engine.config.GameConfig;
import ua.kostenko.battleship.battleship.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.engine.models.Player;
import ua.kostenko.battleship.battleship.engine.models.enums.Direction;
import ua.kostenko.battleship.battleship.engine.models.enums.GameState;
import ua.kostenko.battleship.battleship.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.engine.models.records.GameStateRepresentation;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;
import ua.kostenko.battleship.battleship.engine.utils.FieldUtil;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class GameImplTest {
    private static final String TEST_GAME_ID = "TEST_ID_1";
    private GameStateRepresentation gameRepresentation;
    private Game game;

    public static void addShipsToField(Player player) {
        var ships = player.getShipsNotOnTheField();
        var field = player.getField();
        var iterator = ships.iterator();

        for (int i = 0; i < GameConfig.NUMBER_OF_ROWS; i += 2) {
            for (int j = 0; j < GameConfig.NUMBER_OF_COLUMNS && iterator.hasNext(); j += 5) {
                var coordinate = Coordinate.of(i, j);
                final Ship ship = iterator.next();
                field.addShip(coordinate, ship);
                iterator.remove();
            }
        }
    }

    @BeforeEach
    void beforeEach() {
        gameRepresentation = GameStateRepresentation.builder()
                                                    .sessionId(TEST_GAME_ID)
                                                    .gameEdition(GameEdition.UKRAINIAN)
                                                    .gameState(GameState.INITIALIZED)
                                                    .players(new HashSet<>())
                                                    .build();
        game = Game.wrap(gameRepresentation);
    }

    @Test
    void testCreatePlayer() {
        assertEquals(GameState.INITIALIZED, game.getGameStateRepresentation().gameState());

        assertThrows(IllegalArgumentException.class, () -> game.createPlayer(null, "test"));
        assertThrows(IllegalArgumentException.class, () -> game.createPlayer("test", null));
        assertThrows(IllegalArgumentException.class, () -> game.createPlayer("  ", "test"));
        assertThrows(IllegalArgumentException.class, () -> game.createPlayer("test", "   "));

        var player1 = game.createPlayer("player_1", "player_name_1");
        assertEquals("player_1", player1.getPlayerId());
        assertEquals("player_name_1", player1.getPlayerName());
        assertEquals(GameState.WAITING_FOR_PLAYERS, game.getGameStateRepresentation().gameState());
        assertFalse(player1.isActive());
        assertFalse(player1.isReady());
        assertFalse(player1.isWinner());

        var player2 = game.createPlayer("player_2", "player_name_2");
        assertEquals("player_2", player2.getPlayerId());
        assertEquals("player_name_2", player2.getPlayerName());
        assertEquals(GameState.PREPARATION, game.getGameStateRepresentation().gameState());
        assertFalse(player2.isActive());
        assertFalse(player2.isReady());
        assertFalse(player2.isWinner());

        assertThrows(IllegalStateException.class, () -> game.createPlayer("pl3", "test"));
    }

    @Test
    void testGetPlayer() {
        assertThrows(IllegalArgumentException.class, () -> game.getPlayer(null));
        assertThrows(IllegalArgumentException.class, () -> game.getPlayer(""));
        assertThrows(IllegalArgumentException.class, () -> game.getPlayer("   "));
        assertThrows(IllegalStateException.class, () -> game.getPlayer("player"));

        var player1 = game.createPlayer("player_1", "player_name_1");
        var player2 = game.createPlayer("player_2", "player_name_2");
        assertEquals("player_1", game.getPlayer("player_1").getPlayerId());
        assertEquals(player1, game.getPlayer("player_1"));
        assertEquals("player_2", game.getPlayer("player_2").getPlayerId());
        assertEquals(player2, game.getPlayer("player_2"));

        assertThrows(IllegalArgumentException.class, () -> game.getPlayer("test"));
    }

    @Test
    void testGetOpponent() {
        assertThrows(IllegalArgumentException.class, () -> game.getOpponent(null));
        assertThrows(IllegalArgumentException.class, () -> game.getOpponent(""));
        assertThrows(IllegalArgumentException.class, () -> game.getOpponent("   "));
        assertThrows(IllegalStateException.class, () -> game.getOpponent("player"));

        var player1 = game.createPlayer("player_1", "player_name_1");

        assertThrows(IllegalArgumentException.class, () -> game.getOpponent("player_1"));

        var player2 = game.createPlayer("player_2", "player_name_2");

        assertEquals("player_2", game.getOpponent("player_1").getPlayerId());
        assertEquals(player2, game.getOpponent("player_1"));
        assertEquals("player_1", game.getOpponent("player_2").getPlayerId());
        assertEquals(player1, game.getOpponent("player_2"));

        assertThrows(IllegalArgumentException.class, () -> game.getOpponent("test"));
    }

    @Test
    void testGetAvailableShipsForPlayer() {
        game.createPlayer("player_1", "player_name_1");

        val ships = game.getAvailableShipsForPlayer("player_1");

        assertEquals(10, ships.size());

        assertThrows(IllegalStateException.class,
                     () -> game.addShipToField("player_1", Coordinate.of(0, 0),
                                               ships.stream().findAny().get()));

        game.createPlayer("player_2", "player_name_2");
        game.addShipToField("player_1", Coordinate.of(0, 0), ships.stream().findAny().get());
        val ships2 = game.getAvailableShipsForPlayer("player_1");

        assertEquals(9, ships2.size());
    }

    @Test
    void testGetAllShipsForPlayer() {
        game.createPlayer("player_1", "player_name_1");
        game.createPlayer("player_2", "player_name_2");

        var ships = game.getAvailableShipsForPlayer("player_1");
        var allShips = game.getAllShipsForPlayer("player_1");

        assertEquals(10, ships.size());
        assertEquals(10, allShips.size());
        assertTrue(allShips.containsAll(ships));

        game.addShipToField("player_1", Coordinate.of(0, 0), ships.stream().findAny().get());

        ships = game.getAvailableShipsForPlayer("player_1");
        allShips = game.getAllShipsForPlayer("player_1");
        assertEquals(9, ships.size());
        assertEquals(10, allShips.size());
    }

    @Test
    void testAddShipToField() {
        game.createPlayer("player_1", "player_name_1");

        val ships = game.getAvailableShipsForPlayer("player_1");

        assertEquals(10, ships.size());
        assertTrue(FieldUtil.convertToFlatSet(game.getField("player_1"))
                            .stream()
                            .noneMatch(Cell::hasShip));

        final Ship ship = ships.stream().findAny().get();

        assertThrows(IllegalStateException.class, () ->
                game.addShipToField("player_1", Coordinate.of(0, 0), ship));

        game.createPlayer("player_2", "player_name_2");

        game.addShipToField("player_1", Coordinate.of(0, 0), ship);

        assertThrows(IllegalArgumentException.class,
                     () -> game.addShipToField("player_1", Coordinate.of(0, 0),
                                               Ship.builder()
                                                   .shipId(ship.shipId())
                                                   .shipType(ship.shipType())
                                                   .shipSize(
                                                           ship.shipSize())
                                                   .direction(
                                                           ship.direction() ==
                                                                   Direction.HORIZONTAL ?
                                                                   Direction.VERTICAL :
                                                                   Direction.HORIZONTAL
                                                   )
                                                   .build()));

        val ships2 = game.getAvailableShipsForPlayer("player_1");

        assertEquals(9, ships2.size());
        assertTrue(FieldUtil.convertToFlatSet(game.getField("player_1"))
                            .stream()
                            .anyMatch(Cell::hasShip));

        game.removeShipFromField("player_1", Coordinate.of(0, 0));
        addShipsToField(game.getPlayer("player_1"));
        val allShipsOfPlayer = game.getAllShipsForPlayer("player_2");
        val shipFromList = allShipsOfPlayer.stream().findAny();
        assert shipFromList.isPresent();
        assertThrows(IllegalArgumentException.class,
                     () -> game.addShipToField("player_1", Coordinate.of(9, 9),
                                               shipFromList.get()));

    }

    @Test
    void testRemoveShipFromField() {
        game.createPlayer("player_1", "player_name_1");

        assertThrows(IllegalStateException.class,
                     () -> game.removeShipFromField("player_1", Coordinate.of(0, 0)));

        game.createPlayer("player_2", "player_name_2");

        var fieldNotDeletedOptional = game.removeShipFromField("player_1", Coordinate.of(5, 5));
        assertTrue(fieldNotDeletedOptional.isEmpty());

        addShipsToField(game.getPlayer("player_1"));

        var existingShipOptional = game.removeShipFromField("player_1", Coordinate.of(0, 0));
        assertTrue(existingShipOptional.isPresent());

        var playerHasShipWithReturnedId = game.getAllShipsForPlayer("player_1")
                                              .stream()
                                              .anyMatch(s -> existingShipOptional.get()
                                                                                 .equals(s.shipId()));
        assertTrue(playerHasShipWithReturnedId);
    }

    @Test
    void testMakePlayerReady() {
        var player1 = game.createPlayer("player_1", "player_name_1");

        assertFalse(player1.isActive());
        assertFalse(player1.isReady());
        assertFalse(player1.isWinner());
        assertThrows(IllegalStateException.class, () -> game.makePlayerReady("player_1"));

        var player2 = game.createPlayer("player_2", "player_name_2");

        assertThrows(IllegalStateException.class, () -> game.makePlayerReady("player_1"));
        addShipsToField(player1);
        addShipsToField(player2);

        assertFalse(player2.isActive());
        assertFalse(player2.isReady());
        assertFalse(player2.isWinner());

        game.makePlayerReady("player_2");

        assertFalse(player1.isActive());
        assertFalse(player1.isReady());
        assertFalse(player1.isWinner());
        assertTrue(player2.isActive());
        assertTrue(player2.isReady());
        assertFalse(player2.isWinner());

        game.makePlayerReady("player_1");

        assertThrows(IllegalStateException.class, () -> game.makePlayerReady("player_1"));
    }

    @Test
    void testGetPlayers() {
        var noPlayersList = game.getPlayers();
        assertTrue(noPlayersList.isEmpty());

        game.createPlayer("player_1", "player_name_1");
        var onePlayerList = game.getPlayers();
        assertFalse(onePlayerList.isEmpty());
        assertEquals(1, onePlayerList.size());
        assertTrue(onePlayerList.stream().anyMatch(s -> "player_1".equals(s.getPlayerId())));

        game.createPlayer("player_2", "player_name_2");
        var twoPlayerList = game.getPlayers();
        assertFalse(twoPlayerList.isEmpty());
        assertEquals(2, twoPlayerList.size());
        assertTrue(twoPlayerList.stream().anyMatch(s -> "player_2".equals(s.getPlayerId())));
    }

    @Test
    void testMakeShot() {
        assertThrows(IllegalStateException.class, () -> game.makeShot("any", Coordinate.of(0, 0)));

        game.createPlayer("player_1", "player_name_1");
        assertThrows(IllegalStateException.class,
                     () -> game.makeShot("player_1", Coordinate.of(0, 0)));

        game.createPlayer("player_2", "player_name_2");
        assertThrows(IllegalStateException.class,
                     () -> game.makeShot("player_2", Coordinate.of(0, 0)));

        addShipsToField(game.getPlayer("player_1"));
        assertThrows(IllegalStateException.class,
                     () -> game.makeShot("player_1", Coordinate.of(0, 0)));

        addShipsToField(game.getPlayer("player_2"));
        assertThrows(IllegalStateException.class,
                     () -> game.makeShot("player_2", Coordinate.of(0, 0)));

        game.makePlayerReady("player_1");
        assertThrows(IllegalStateException.class,
                     () -> game.makeShot("player_2", Coordinate.of(0, 0)));

        game.makePlayerReady("player_2");

        var shotResultHit = game.makeShot("player_1", Coordinate.of(0, 0));
        assertTrue(Set.of(ShotResult.HIT, ShotResult.DESTROYED).contains(shotResultHit));
        var shotResultMiss = game.makeShot("player_1", Coordinate.of(1, 0));
        assertEquals(ShotResult.MISS, shotResultMiss);

    }

    @Test
    void testGetField() {
        assertThrows(IllegalStateException.class, () -> game.getField("player_1"));
        game.createPlayer("player_1", "player_name");
        var field = game.getField("player_1");
        assertNotNull(field);
        assertEquals(100, FieldUtil.convertToFlatSet(field).size());
    }

    @Test
    void testGetOpponentField() {
        assertThrows(IllegalStateException.class, () -> game.getOpponent("player_1"));

        game.createPlayer("player_1", "player_name");
        assertThrows(IllegalArgumentException.class, () -> game.getOpponent("player_1"));

        game.createPlayer("player_2", "player_name_2");
        var field = game.getOpponentField("player_1");
        assertNotNull(field);
        assertEquals(100, FieldUtil.convertToFlatSet(field).size());
    }

    @Test
    void testGetWinner() {
        assertTrue(game.getWinner().isEmpty());

        var player1 = game.createPlayer("player_1", "player_name_1");
        var player2 = game.createPlayer("player_2", "player_name_2");
        addShipsToField(player1);
        addShipsToField(player2);
        game.makePlayerReady(player1.getPlayerId());
        game.makePlayerReady(player2.getPlayerId());

        assertTrue(game.getWinner().isEmpty());

        game.makeShot(player1.getPlayerId(), Coordinate.of(1, 0));

        assertThrows(IllegalStateException.class,
                     () -> game.makeShot(player1.getPlayerId(), Coordinate.of(1, 1)));

        game.makeShot(player2.getPlayerId(), Coordinate.of(1, 0));

        assertTrue(game.getWinner().isEmpty());

        FieldUtil.convertToFlatSet(player2.getField().getField()).stream()
                 .filter(Cell::hasShip)
                 .filter(c -> !c.hasShot())
                 .map(Cell::coordinate)
                 .forEach(c -> game.makeShot(player1.getPlayerId(), c));

        var winner = game.getWinner();
        assertTrue(winner.isPresent());
        assertEquals(player1, winner.get());
    }

    @Test
    void testGetGameStateRepresentation() {
        var state1 = game.getGameStateRepresentation();
        assertEquals(GameState.INITIALIZED, state1.gameState());
        assertTrue(state1.players().isEmpty());

        game.createPlayer("player_1", "name_1");
        var state2 = game.getGameStateRepresentation();
        assertEquals(GameState.WAITING_FOR_PLAYERS, state2.gameState());
        assertEquals(1, state2.players().size());

        game.createPlayer("player_2", "name_2");
        var state3 = game.getGameStateRepresentation();
        assertEquals(GameState.PREPARATION, state3.gameState());
        assertEquals(2, state3.players().size());

        addShipsToField(game.getPlayer("player_1"));
        addShipsToField(game.getPlayer("player_2"));
        game.makePlayerReady("player_1");
        game.makePlayerReady("player_2");

        var state4 = game.getGameStateRepresentation();
        assertEquals(GameState.IN_GAME, state4.gameState());
        assertEquals(2, state4.players().size());

        FieldUtil.convertToFlatSet(game.getPlayer("player_2").getField().getField()).stream()
                 .filter(Cell::hasShip)
                 .map(Cell::coordinate)
                 .forEach(c -> game.makeShot("player_1", c));

        var state5 = game.getGameStateRepresentation();
        assertEquals(GameState.FINISHED, state5.gameState());
        assertEquals(2, state5.players().size());
    }
}
