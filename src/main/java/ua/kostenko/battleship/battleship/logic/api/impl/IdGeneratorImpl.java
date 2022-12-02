package ua.kostenko.battleship.battleship.logic.api.impl;

import ua.kostenko.battleship.battleship.logic.api.IdGenerator;

import java.util.UUID;

public class IdGeneratorImpl implements IdGenerator {
    @Override
    public String generateId() {
        return UUID.randomUUID()
                   .toString();
    }
}
