package ua.kostenko.battleship.app.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ua.kostenko.battleship.app.config.BattleshipProperties;
import ua.kostenko.battleship.app.web.dto.CommandRequest;
import ua.kostenko.battleship.app.web.dto.FireCommand;
import ua.kostenko.battleship.app.web.dto.GameSnapshot;
import ua.kostenko.battleship.app.web.dto.PlaceShipCommand;
import ua.kostenko.battleship.app.web.dto.RemoveShipCommand;
import ua.kostenko.battleship.app.web.dto.SimpleCommand;
import ua.kostenko.battleship.application.usecase.CommandUseCase;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.Orientation;

/** Applies one player action and answers with the caller's snapshot (R23, R24, R46). */
@RestController
class CommandController {
    private final CommandUseCase commands;
    private final int randomArrangementAttempts;

    CommandController(CommandUseCase commands, BattleshipProperties properties) {
        this.commands = commands;
        this.randomArrangementAttempts = properties.randomArrangementAttempts();
    }

    @PostMapping("/api/v1/games/{gameId}/commands")
    GameSnapshot sendCommand(
            @PathVariable String gameId,
            @Valid @RequestBody CommandRequest request,
            @CookieValue(name = SessionCookie.NAME, required = false) String presentedSession) {
        GameCommand command = toDomain(request.getCommand().getActualInstance());
        return SnapshotDtoAssembler.assemble(commands.execute(gameId, presentedSession, request.getCommandId(), command)
                .snapshot());
    }

    private GameCommand toDomain(Object variant) {
        return switch (variant) {
            case PlaceShipCommand place ->
                new GameCommand.PlaceShip(
                        place.getShipId(),
                        coordinate(place.getAnchor()),
                        Orientation.valueOf(place.getOrientation().name()));
            case RemoveShipCommand remove -> new GameCommand.RemoveShip(remove.getShipId());
            case FireCommand fire -> new GameCommand.Fire(coordinate(fire.getTarget()));
            case SimpleCommand simple ->
                switch (simple.getType()) {
                    case PLACE_FLEET_RANDOMLY -> new GameCommand.PlaceFleetRandomly(randomArrangementAttempts);
                    case CLEAR_FLEET -> new GameCommand.ClearFleet();
                    case READY -> new GameCommand.Ready();
                    case RESIGN -> new GameCommand.Resign();
                };
            default -> throw new IllegalStateException("unmapped command variant " + variant.getClass());
        };
    }

    private static Coordinate coordinate(ua.kostenko.battleship.app.web.dto.Coordinate wire) {
        return new Coordinate(wire.getRowIndex(), wire.getColumnIndex());
    }
}
