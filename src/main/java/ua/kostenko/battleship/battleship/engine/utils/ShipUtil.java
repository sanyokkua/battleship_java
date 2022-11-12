package ua.kostenko.battleship.battleship.engine.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.kostenko.battleship.battleship.engine.config.GameConfig;
import ua.kostenko.battleship.battleship.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.engine.config.ShipConfiguration;
import ua.kostenko.battleship.battleship.engine.models.enums.Direction;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShipUtil {

    public static Set<Ship> generateShips(GameEdition gameEdition) {
        log.trace("In method: generateShips");
        log.debug("GameEdition: {}", gameEdition);
        final Set<ShipConfiguration> shipConfigs = GameConfig.getConfiguration(gameEdition);
        return shipConfigs.stream()
                          .flatMap(ship -> {
                              List<Ship> ships = new ArrayList<>();
                              for (int i = 0; i < ship.shipAmount(); i++) {
                                  ships.add(Ship.builder()
                                                .shipType(ship.shipType())
                                                .shipSize(ship.shipSize())
                                                .direction(Direction.HORIZONTAL)
                                                .shipId(UUID.randomUUID().toString())
                                                .build());
                              }
                              return ships.stream();
                          })
                          .collect(Collectors.toSet());
    }
}
