package ua.kostenko.battleship.application.projection;

import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.ShotResult;

public record ShotView(SnapshotView.Side by, Coordinate target, ShotResult result, String sunkShipId) {}
