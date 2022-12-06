package ua.kostenko.battleship.battleship.logic.engine;

import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEditionConfiguration;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipDirection;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.models.records.GameState;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;
import ua.kostenko.battleship.battleship.logic.engine.utils.FieldUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("ALL")
public class GameImplTest {
    private static final String TEST_GAME_ID = "TEST_ID_1";
    private Game game;

    public static void addShipsToField(Player player) {
        var ships = player.getShipsNotOnTheField();
        var field = player.getFieldManagement();
        var iterator = ships.iterator();

        for (int i = 0; i < GameEditionConfiguration.NUMBER_OF_ROWS; i += 2) {
            for (int j = 0; j < GameEditionConfiguration.NUMBER_OF_COLUMNS && iterator.hasNext(); j += 5) {
                var coordinate = Coordinate.of(i, j);
                final Ship ship = iterator.next();
                field.addShip(coordinate, ship);
                iterator.remove();
            }
        }
    }

    @BeforeEach
    void beforeEach() {
        GameState gameRepresentation = GameState.create(GameEdition.UKRAINIAN, TEST_GAME_ID, GameStage.INITIALIZED);
        game = Game.fromGameState(gameRepresentation);
    }

    @Test
    void testCreatePlayer() {
        assertEquals(GameStage.INITIALIZED,
                     game.getGameState()
                         .gameStage());

        assertThrows(IllegalArgumentException.class, () -> game.createPlayer(null, "test"));
        assertThrows(IllegalArgumentException.class, () -> game.createPlayer("test", null));
        assertThrows(IllegalArgumentException.class, () -> game.createPlayer("  ", "test"));
        assertThrows(IllegalArgumentException.class, () -> game.createPlayer("test", "   "));

        var player1 = game.createPlayer("player_1", "player_name_1");
        assertEquals("player_1", player1.getPlayerId());
        assertEquals("player_name_1", player1.getPlayerName());
        assertEquals(GameStage.WAITING_FOR_PLAYERS,
                     game.getGameState()
                         .gameStage());
        assertFalse(player1.isActive());
        assertFalse(player1.isReady());
        assertFalse(player1.isWinner());

        var player2 = game.createPlayer("player_2", "player_name_2");
        assertEquals("player_2", player2.getPlayerId());
        assertEquals("player_name_2", player2.getPlayerName());
        assertEquals(GameStage.PREPARATION,
                     game.getGameState()
                         .gameStage());
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
        assertEquals("player_1",
                     game.getPlayer("player_1")
                         .getPlayerId());
        assertEquals(player1, game.getPlayer("player_1"));
        assertEquals("player_2",
                     game.getPlayer("player_2")
                         .getPlayerId());
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

        assertEquals("player_2",
                     game.getOpponent("player_1")
                         .getPlayerId());
        assertEquals(player2, game.getOpponent("player_1"));
        assertEquals("player_1",
                     game.getOpponent("player_2")
                         .getPlayerId());
        assertEquals(player1, game.getOpponent("player_2"));

        assertThrows(IllegalArgumentException.class, () -> game.getOpponent("test"));
    }

    @Test
    void testGetAvailableShipsForPlayer() {
        game.createPlayer("player_1", "player_name_1");

        val ships = game.getShipsNotOnTheField("player_1");

        assertEquals(10, ships.size());

        assertThrows(IllegalStateException.class,
                     () -> game.addShipToField("player_1",
                                               Coordinate.of(0, 0),
                                               ships.stream()
                                                    .findAny()
                                                    .get()));

        game.createPlayer("player_2", "player_name_2");
        game.addShipToField("player_1",
                            Coordinate.of(0, 0),
                            ships.stream()
                                 .findAny()
                                 .get());
        val ships2 = game.getShipsNotOnTheField("player_1");

        assertEquals(9, ships2.size());
    }

    @Test
    void testGetAllShipsForPlayer() {
        game.createPlayer("player_1", "player_name_1");
        game.createPlayer("player_2", "player_name_2");

        var ships = game.getShipsNotOnTheField("player_1");
        var allShips = game.getAllShips("player_1");

        assertEquals(10, ships.size());
        assertEquals(10, allShips.size());
        assertTrue(allShips.containsAll(ships));

        game.addShipToField("player_1",
                            Coordinate.of(0, 0),
                            ships.stream()
                                 .findAny()
                                 .get());

        ships = game.getShipsNotOnTheField("player_1");
        allShips = game.getAllShips("player_1");
        assertEquals(9, ships.size());
        assertEquals(10, allShips.size());
    }

    @Test
    void testAddShipToField() {
        game.createPlayer("player_1", "player_name_1");

        val ships = game.getShipsNotOnTheField("player_1");

        assertEquals(10, ships.size());
        assertTrue(FieldUtils.convertToFlatSet(game.getField("player_1"))
                             .stream()
                             .noneMatch(Cell::hasShip));

        final Ship ship = ships.stream()
                               .findAny()
                               .get();

        assertThrows(IllegalStateException.class, () -> game.addShipToField("player_1", Coordinate.of(0, 0), ship));

        game.createPlayer("player_2", "player_name_2");

        game.addShipToField("player_1", Coordinate.of(0, 0), ship);

        assertThrows(IllegalArgumentException.class,
                     () -> game.addShipToField("player_1",
                                               Coordinate.of(0, 0),
                                               Ship.builder()
                                                   .shipId(ship.shipId())
                                                   .shipType(ship.shipType())
                                                   .shipSize(ship.shipSize())
                                                   .shipDirection(ship.shipDirection() == ShipDirection.HORIZONTAL ?
                                                                          ShipDirection.VERTICAL :
                                                                          ShipDirection.HORIZONTAL)
                                                   .build()));

        val ships2 = game.getShipsNotOnTheField("player_1");

        assertEquals(9, ships2.size());
        assertTrue(FieldUtils.convertToFlatSet(game.getField("player_1"))
                             .stream()
                             .anyMatch(Cell::hasShip));

        game.removeShipFromField("player_1", Coordinate.of(0, 0));
        addShipsToField(game.getPlayer("player_1"));
        val allShipsOfPlayer = game.getAllShips("player_2");
        val shipFromList = allShipsOfPlayer.stream()
                                           .findAny();
        assert shipFromList.isPresent();
        assertThrows(IllegalArgumentException.class, () -> game.addShipToField("player_1", Coordinate.of(9, 9), shipFromList.get()));

    }

    @Test
    void testRemoveShipFromField() {
        game.createPlayer("player_1", "player_name_1");

        assertThrows(IllegalStateException.class, () -> game.removeShipFromField("player_1", Coordinate.of(0, 0)));

        game.createPlayer("player_2", "player_name_2");

        var fieldNotDeletedOptional = game.removeShipFromField("player_1", Coordinate.of(5, 5));
        assertTrue(fieldNotDeletedOptional.isEmpty());

        addShipsToField(game.getPlayer("player_1"));

        var existingShipOptional = game.removeShipFromField("player_1", Coordinate.of(0, 0));
        assertTrue(existingShipOptional.isPresent());

        var playerHasShipWithReturnedId = game.getAllShips("player_1")
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
        assertThrows(IllegalStateException.class, () -> game.changePlayerStatusToReady("player_1"));

        var player2 = game.createPlayer("player_2", "player_name_2");

        assertThrows(IllegalStateException.class, () -> game.changePlayerStatusToReady("player_1"));
        addShipsToField(player1);
        addShipsToField(player2);

        assertFalse(player2.isActive());
        assertFalse(player2.isReady());
        assertFalse(player2.isWinner());

        game.changePlayerStatusToReady("player_2");

        assertFalse(player1.isActive());
        assertFalse(player1.isReady());
        assertFalse(player1.isWinner());
        assertTrue(player2.isActive());
        assertTrue(player2.isReady());
        assertFalse(player2.isWinner());

        game.changePlayerStatusToReady("player_1");

        assertThrows(IllegalStateException.class, () -> game.changePlayerStatusToReady("player_1"));
    }

    @Test
    void testGetPlayers() {
        var noPlayersList = game.getPlayers();
        assertTrue(noPlayersList.isEmpty());

        game.createPlayer("player_1", "player_name_1");
        var onePlayerList = game.getPlayers();
        assertFalse(onePlayerList.isEmpty());
        assertEquals(1, onePlayerList.size());
        assertTrue(onePlayerList.stream()
                                .anyMatch(s -> "player_1".equals(s.getPlayerId())));

        game.createPlayer("player_2", "player_name_2");
        var twoPlayerList = game.getPlayers();
        assertFalse(twoPlayerList.isEmpty());
        assertEquals(2, twoPlayerList.size());
        assertTrue(twoPlayerList.stream()
                                .anyMatch(s -> "player_2".equals(s.getPlayerId())));
    }

    @Test
    void testMakeShot() {
        assertThrows(IllegalStateException.class, () -> game.makeShot("any", Coordinate.of(0, 0)));

        game.createPlayer("player_1", "player_name_1");
        assertThrows(IllegalStateException.class, () -> game.makeShot("player_1", Coordinate.of(0, 0)));

        game.createPlayer("player_2", "player_name_2");
        assertThrows(IllegalStateException.class, () -> game.makeShot("player_2", Coordinate.of(0, 0)));

        addShipsToField(game.getPlayer("player_1"));
        assertThrows(IllegalStateException.class, () -> game.makeShot("player_1", Coordinate.of(0, 0)));

        addShipsToField(game.getPlayer("player_2"));
        assertThrows(IllegalStateException.class, () -> game.makeShot("player_2", Coordinate.of(0, 0)));

        game.changePlayerStatusToReady("player_1");
        assertThrows(IllegalStateException.class, () -> game.makeShot("player_2", Coordinate.of(0, 0)));

        game.changePlayerStatusToReady("player_2");

        var shotResultHit = game.makeShot("player_1", Coordinate.of(0, 0));
        assertTrue(Set.of(ShotResult.HIT, ShotResult.DESTROYED)
                      .contains(shotResultHit));
        var shotResultMiss = game.makeShot("player_1", Coordinate.of(1, 0));
        assertEquals(ShotResult.MISS, shotResultMiss);

    }

    @Test
    void testGetField() {
        assertThrows(IllegalStateException.class, () -> game.getField("player_1"));
        game.createPlayer("player_1", "player_name");
        var field = game.getField("player_1");
        assertNotNull(field);
        assertEquals(100,
                     FieldUtils.convertToFlatSet(field)
                               .size());
    }

    @Test
    void testGetOpponentField() {
        assertThrows(IllegalStateException.class, () -> game.getOpponent("player_1"));

        game.createPlayer("player_1", "player_name");
        assertThrows(IllegalArgumentException.class, () -> game.getOpponent("player_1"));

        game.createPlayer("player_2", "player_name_2");
        var field = game.getOpponentField("player_1");
        assertNotNull(field);
        assertEquals(100,
                     FieldUtils.convertToFlatSet(field)
                               .size());
    }

    @Test
    void testGetWinner() {
        assertTrue(game.getWinner()
                       .isEmpty());

        var player1 = game.createPlayer("player_1", "player_name_1");
        var player2 = game.createPlayer("player_2", "player_name_2");
        addShipsToField(player1);
        addShipsToField(player2);
        game.changePlayerStatusToReady(player1.getPlayerId());
        game.changePlayerStatusToReady(player2.getPlayerId());

        assertTrue(game.getWinner()
                       .isEmpty());

        game.makeShot(player1.getPlayerId(), Coordinate.of(1, 0));

        assertThrows(IllegalStateException.class, () -> game.makeShot(player1.getPlayerId(), Coordinate.of(1, 1)));

        game.makeShot(player2.getPlayerId(), Coordinate.of(1, 0));

        assertTrue(game.getWinner()
                       .isEmpty());

        FieldUtils.convertToFlatSet(player2.getFieldManagement()
                                           .getField())
                  .stream()
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
        var state1 = game.getGameState();
        assertEquals(GameStage.INITIALIZED, state1.gameStage());
        assertTrue(state1.players()
                         .isEmpty());

        game.createPlayer("player_1", "name_1");
        var state2 = game.getGameState();
        assertEquals(GameStage.WAITING_FOR_PLAYERS, state2.gameStage());
        assertEquals(1,
                     state2.players()
                           .size());

        game.createPlayer("player_2", "name_2");
        var state3 = game.getGameState();
        assertEquals(GameStage.PREPARATION, state3.gameStage());
        assertEquals(2,
                     state3.players()
                           .size());

        addShipsToField(game.getPlayer("player_1"));
        addShipsToField(game.getPlayer("player_2"));
        game.changePlayerStatusToReady("player_1");
        game.changePlayerStatusToReady("player_2");

        var state4 = game.getGameState();
        assertEquals(GameStage.IN_GAME, state4.gameStage());
        assertEquals(2,
                     state4.players()
                           .size());

        FieldUtils.convertToFlatSet(game.getPlayer("player_2")
                                        .getFieldManagement()
                                        .getField())
                  .stream()
                  .filter(Cell::hasShip)
                  .map(Cell::coordinate)
                  .forEach(c -> game.makeShot("player_1", c));

        var state5 = game.getGameState();
        assertEquals(GameStage.FINISHED, state5.gameStage());
        assertEquals(2,
                     state5.players()
                           .size());
    }
}
