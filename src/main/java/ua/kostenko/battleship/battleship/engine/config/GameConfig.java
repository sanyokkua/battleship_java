package ua.kostenko.battleship.battleship.engine.config;

import lombok.val;
import ua.kostenko.battleship.battleship.engine.models.enums.ShipType;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class GameConfig {
    public static final int NUMBER_OF_ROWS = 10;
    public static final int NUMBER_OF_COLUMNS = 10;

    public static Set<ShipConfiguration> getConfiguration(GameType gameType) {
        switch (gameType) {
            case CUSTOM -> {
                return new CustomGameConfig().getShipConfigs();
            }
            case CLASSIC -> {
                return new ClassicGameConfig().getShipConfigs();
            }
            default -> {
                throw new IllegalArgumentException(
                        "GameType %s is not supported yet".formatted(gameType));
            }
        }
    }

    protected abstract Map<ShipType, Integer> getSizeMapping();

    protected abstract Map<ShipType, Integer> getAmountMapping();

    protected Set<ShipConfiguration> getShipConfigs() {
        val amountMapping = this.getAmountMapping();
        val sizeMapping = this.getSizeMapping();
        return amountMapping.entrySet()
                            .stream()
                            .map(entry -> {
                                val shipType = entry.getKey();
                                val amount = entry.getValue();
                                val optionalSize = Optional.ofNullable(sizeMapping.get(shipType));
                                if (optionalSize.isEmpty()) {
                                    val message = "For shipType %s there is no size mapping";
                                    throw new IllegalArgumentException(message.formatted(shipType));
                                }
                                return ShipConfiguration.builder()
                                                        .shipType(shipType)
                                                        .shipSize(optionalSize.get())
                                                        .shipAmount(amount)
                                                        .build();
                            })
                            .collect(Collectors.toSet());
    }

}
