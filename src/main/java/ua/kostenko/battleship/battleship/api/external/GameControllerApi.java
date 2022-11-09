package ua.kostenko.battleship.battleship.api.external;

import org.springframework.http.ResponseEntity;
import ua.kostenko.battleship.battleship.api.dtos.PlayerDto;
import ua.kostenko.battleship.battleship.engine.config.GameType;

public interface GameControllerApi {

    // POST
    ResponseEntity<String> createGameSession(GameType gameType);

    // POST
    ResponseEntity<PlayerDto> createPlayerInSession(String sessionId, String playerName);

    // POST
    ResponseEntity<String> startGame(String sessionId, String playerId);
}
