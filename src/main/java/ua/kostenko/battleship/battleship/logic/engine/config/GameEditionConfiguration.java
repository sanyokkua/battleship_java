package ua.kostenko.battleship.battleship.logic.engine.config;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipType;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public abstract class GameEditionConfiguration {
    public static final int NUMBER_OF_ROWS = 10;
    public static final int NUMBER_OF_COLUMNS = 10;

    public static Set<ShipConfiguration> getConfiguration(GameEdition gameEdition) {
        log.trace("In method: getConfiguration");
        switch (gameEdition) {
            case MILTON_BRADLEY -> {
                log.debug("MILTON_BRADLEY edition config will be returned");
                return new MiltonBradleyGameEditionConfiguration().getShipConfigs();
            }
            case UKRAINIAN -> {
                log.debug("UKRAINIAN edition config will be returned");
                return new UkrainianGameEditionConfiguration().getShipConfigs();
            }
            default -> throw new IllegalArgumentException("GameType %s is not supported yet".formatted(gameEdition));
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
