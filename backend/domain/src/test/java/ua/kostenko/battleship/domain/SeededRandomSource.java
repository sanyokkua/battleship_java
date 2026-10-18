package ua.kostenko.battleship.domain;

import java.util.Random;

public final class SeededRandomSource implements RandomSource {
    private final Random random;

    public SeededRandomSource(long seed) {
        random = new Random(seed);
    }

    @Override
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }
}
