package ua.kostenko.battleship.battleship.logic.api.impl;

import ua.kostenko.battleship.battleship.logic.api.IdGenerator;

import java.util.UUID;

/**
 * Implementation of the {@link IdGenerator} interface.
 * <p>
 * The IdGeneratorImpl class provides a method to generate unique identifiers using UUID.
 * </p>
 *
 * @see IdGenerator
 */
public class IdGeneratorImpl implements IdGenerator {

    /**
     * Generates a unique identifier using UUID.
     *
     * @return a generated unique identifier as a {@link String}
     */
    @Override
    public String generateId() {
        return UUID.randomUUID().toString();
    }
}
