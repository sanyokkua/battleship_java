package ua.kostenko.battleship.battleship.logic.api;

import java.util.UUID;

public class IdGeneratorImpl implements IdGenerator {
    @Override
    public String generateId() {
        return UUID.randomUUID()
                   .toString();
    }
}
