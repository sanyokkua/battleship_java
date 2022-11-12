package ua.kostenko.battleship.battleship.logic.engine.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShipUtils {

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
                                                .shipId(UUID.randomUUID()
                                                            .toString())
                                                .build());
                              }
                              return ships.stream();
                          })
                          .collect(Collectors.toSet());
    }
}
