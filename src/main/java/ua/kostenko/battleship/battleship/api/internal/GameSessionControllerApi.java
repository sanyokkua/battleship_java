package ua.kostenko.battleship.battleship.api.internal;

import ua.kostenko.battleship.battleship.api.dtos.PlayerDto;
import ua.kostenko.battleship.battleship.engine.config.GameEdition;

import java.util.Optional;

public interface GameSessionControllerApi {

    Optional<String> createGameSession(GameEdition gameEdition);

    Optional<PlayerDto> createPlayerInSession(String sessionId, String playerName);

    Optional<PlayerDto> startGame(String sessionId, String playerId);
}
