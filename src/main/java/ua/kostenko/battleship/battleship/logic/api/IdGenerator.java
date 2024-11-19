package ua.kostenko.battleship.battleship.logic.api;

/**
 * Interface representing an ID generator for the Battleship game.
 * <p>
 * The IdGenerator interface defines a method to generate unique identifiers,
 * which can be used for various entities within the game.
 * </p>
 */
public interface IdGenerator {

    /**
     * Generates a unique identifier.
     *
     * @return a generated unique identifier as a {@link String}
     */
    String generateId();
}
