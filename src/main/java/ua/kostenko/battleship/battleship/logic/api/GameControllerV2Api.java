package ua.kostenko.battleship.battleship.logic.api;

import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.models.GameplayState;
import ua.kostenko.battleship.battleship.logic.engine.models.OpponentInfo;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;

import java.util.List;

public interface GameControllerV2Api {
    List<GameEdition> getAvailableGameEditions();

    String createGameSession(String gameEdition);

    Player createPlayerInSession(String sessionId, String playerName);

    GameStage getCurrentGameStage(String sessionId);

    String getLastSessionChangeTime(String sessionId);

    List<Ship> getShipsNotOnTheBoard(String sessionId, String playerId);

    Ship addShipToField(
            String sessionId, String playerId, String shipId, Coordinate coordinate, String direction);

    String removeShipFromField(String sessionId, String playerId, Coordinate coordinate);

    OpponentInfo getOpponentInformation(String sessionId, String playerId);

    Cell[][] getPreparationField(String sessionId, String playerId);

    Player startGame(String sessionId, String playerId);

    GameplayState getGameState(String sessionId, String playerId);

    ShotResult makeShotByField(String sessionId, String playerId, Coordinate coordinate);
}
