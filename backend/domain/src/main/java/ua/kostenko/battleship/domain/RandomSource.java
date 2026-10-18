package ua.kostenko.battleship.domain;

@FunctionalInterface
public interface RandomSource {
    int nextInt(int bound);
}
