package ua.kostenko.battleship.battleship.logic.engine.config;

import lombok.extern.log4j.Log4j2;
import lombok.val;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipType;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Abstract class representing the configuration of different game editions in the Battleship game.
 * <p>
 * The GameEditionConfiguration class provides methods to retrieve configuration settings such as the size
 * and amount of ships for different editions of the game.
 * </p>
 *
 * @see ShipType
 * @see MiltonBradleyGameEditionConfiguration
 * @see UkrainianGameEditionConfiguration
 */
@Log4j2
public abstract class GameEditionConfiguration {

    /**
     * The number of rows on the game board.
     */
    public static final int NUMBER_OF_ROWS = 10;

    /**
     * The number of columns on the game board.
     */
    public static final int NUMBER_OF_COLUMNS = 10;

    /**
     * Retrieves the configuration for the specified game edition.
     *
     * @param gameEdition the edition of the game for which the configuration is required
     * @return a set of {@link ShipConfiguration} for the specified game edition
     */
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

    /**
     * Gets the size mapping for ships in the game edition.
     *
     * @return a map with ship types as keys and their corresponding sizes as values
     */
    protected abstract Map<ShipType, Integer> getSizeMapping();

    /**
     * Gets the amount mapping for ships in the game edition.
     *
     * @return a map with ship types as keys and their corresponding amounts as values
     */
    protected abstract Map<ShipType, Integer> getAmountMapping();

    /**
     * Retrieves the ship configurations for the game edition.
     *
     * @return a set of {@link ShipConfiguration} representing the ship configurations
     */
    protected Set<ShipConfiguration> getShipConfigs() {
        val amountMapping = this.getAmountMapping();
        val sizeMapping = this.getSizeMapping();
        return amountMapping.entrySet().stream().map(entry -> {
            val shipType = entry.getKey();
            val amount = entry.getValue();
            val optionalSize = Optional.ofNullable(sizeMapping.get(shipType));
            if (optionalSize.isEmpty()) {
                val message = "For shipType %s there is no size mapping";
                throw new IllegalArgumentException(message.formatted(shipType));
            }
            return ShipConfiguration.builder().shipType(shipType).shipSize(optionalSize.get()).shipAmount(amount).build();
        }).collect(Collectors.toSet());
    }
}