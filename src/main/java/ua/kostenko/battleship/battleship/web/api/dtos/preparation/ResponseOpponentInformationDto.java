package ua.kostenko.battleship.battleship.web.api.dtos.preparation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.models.OpponentInfo;

/**
 * Data Transfer Object (DTO) for opponent information in the Battleship game.
 * <p>
 * The ResponseOpponentInformationDto class is used to transfer data related to an opponent, including their name and readiness status.
 * </p>
 *
 * @see OpponentInfo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseOpponentInformationDto {

    /**
     * The name of the opponent player.
     */
    @Schema(description = "Opponent's display name")
    private String playerName;

    /**
     * Indicates whether the opponent player is ready.
     */
    @Schema(description = "Whether the opponent has readied up")
    private boolean ready;

    /**
     * Creates a ResponseOpponentInformationDto object from an OpponentInfo object.
     *
     * @param opponentInfo the OpponentInfo object to convert to ResponseOpponentInformationDto
     * @return the created ResponseOpponentInformationDto object
     */
    public static ResponseOpponentInformationDto from(OpponentInfo opponentInfo) {
        return ResponseOpponentInformationDto.builder()
                .playerName(opponentInfo.playerName())
                .ready(opponentInfo.isReady())
                .build();
    }
}
