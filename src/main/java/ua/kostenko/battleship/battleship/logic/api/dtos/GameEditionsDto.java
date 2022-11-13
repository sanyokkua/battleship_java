package ua.kostenko.battleship.battleship.logic.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameEditionsDto {
    private Set<GameEdition> gameEditions;
}
