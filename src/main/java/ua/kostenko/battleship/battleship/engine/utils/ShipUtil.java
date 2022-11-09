package ua.kostenko.battleship.battleship.engine.utils;

import ua.kostenko.battleship.battleship.engine.config.GameConfig;
import ua.kostenko.battleship.battleship.engine.config.GameType;
import ua.kostenko.battleship.battleship.engine.config.ShipConfiguration;
import ua.kostenko.battleship.battleship.engine.models.enums.Direction;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ShipUtil {

    public static Set<Ship> generateShips(GameType gameType) {
        final Set<ShipConfiguration> shipConfigs = GameConfig.getConfiguration(gameType);
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
