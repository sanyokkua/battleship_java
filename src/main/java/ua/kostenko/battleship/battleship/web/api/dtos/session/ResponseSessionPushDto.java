package ua.kostenko.battleship.battleship.web.api.dtos.session;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.web.api.dtos.gameplay.ResponseGameplayStateDto;
import ua.kostenko.battleship.battleship.web.api.dtos.preparation.ResponseOpponentInformationDto;

/**
 * Data Transfer Object (DTO) pushed to subscribed clients over Server-Sent Events whenever a
 * session's state changes.
 * <p>
 * Wraps the existing stable response shapes rather than duplicating their fields: {@code opponent}
 * is populated once an opponent has joined the session, and {@code gameplayState} is populated
 * once an opponent has joined and the session is {@code IN_GAME} or {@code FINISHED}. Both are
 * {@code null} otherwise.
 * </p>
 *
 * @see ResponseOpponentInformationDto
 * @see ResponseGameplayStateDto
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseSessionPushDto {

    /**
     * The current game stage, represented as a string.
     */
    @Schema(description = "Current GameStage name",
            allowableValues = {"INITIALIZED", "WAITING_FOR_PLAYERS", "PREPARATION", "IN_GAME", "FINISHED"})
    private String gameStage;

    /**
     * The time of the last session change.
     */
    @Schema(description = "Last-update timestamp as a string")
    private String lastUpdate;

    /**
     * The opponent information for the subscribing player, if an opponent has joined.
     */
    @Schema(description = "Opponent information for the subscribing player, null until an opponent has joined")
    private ResponseOpponentInformationDto opponent;

    /**
     * The gameplay state for the subscribing player, once the session is IN_GAME or FINISHED.
     */
    @Schema(description = "Gameplay state for the subscribing player, null until the session is IN_GAME or FINISHED")
    private ResponseGameplayStateDto gameplayState;
}
