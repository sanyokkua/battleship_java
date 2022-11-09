package ua.kostenko.battleship.battleship.api;

import ua.kostenko.battleship.battleship.api.dtos.PlayerDto;
import ua.kostenko.battleship.battleship.engine.config.GameType;

import java.util.Optional;

public interface GameSessionControllerApi {

    Optional<String> createGameSession(GameType gameType);

    Optional<PlayerDto> createPlayerInSession(String sessionId, String playerName);

    Optional<PlayerDto> startGame(String sessionId, String playerId);
}
