package ua.kostenko.battleship.domain.model;

public enum Seat {
    HOST,
    GUEST;

    public Seat opponent() {
        return this == HOST ? GUEST : HOST;
    }
}
