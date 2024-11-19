package ua.kostenko.battleship.battleship.logic.engine.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEditionConfiguration;
import ua.kostenko.battleship.battleship.logic.engine.config.ShipConfiguration;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipDirection;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Utility class for handling ship operations in the Battleship game.
 * <p>
 * The ShipUtils class provides methods to generate ships based on the game edition configuration.
 * </p>
 *
 * @see GameEdition
 * @see GameEditionConfiguration
 * @see ShipConfiguration
 * @see ShipDirection
 * @see Ship
 */
@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShipUtils {

    /**
     * Generates a set of ships based on the specified game edition.
     *
     * @param gameEdition the edition of the game for which ships need to be generated
     * @return a set of ships configured for the specified game edition
     */
    public static Set<Ship> generateShips(GameEdition gameEdition) {
        log.trace("In method: generateShips");
        log.debug("GameEdition: {}", gameEdition);
        final Set<ShipConfiguration> shipConfigs = GameEditionConfiguration.getConfiguration(gameEdition);
        return shipConfigs.stream()
                .flatMap(ship -> {
                    List<Ship> ships = new ArrayList<>();
                    for (int i = 0; i < ship.shipAmount(); i++) {
                        ships.add(Ship.builder()
                                .shipType(ship.shipType())
                                .shipSize(ship.shipSize())
                                .shipDirection(ShipDirection.HORIZONTAL)
                                .shipId(UUID.randomUUID().toString())
                                .build());
                    }
                    return ships.stream();
                })
                .collect(Collectors.toSet());
    }
}
