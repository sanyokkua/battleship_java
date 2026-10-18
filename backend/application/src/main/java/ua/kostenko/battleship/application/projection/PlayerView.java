package ua.kostenko.battleship.application.projection;

public record PlayerView(String displayName, boolean ready, boolean connected, int shipsRemaining) {}
