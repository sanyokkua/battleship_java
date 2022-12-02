package ua.kostenko.battleship.battleship.web.controllers.api.dtos.preparation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponsePlayerReady {
    private boolean ready;
}
