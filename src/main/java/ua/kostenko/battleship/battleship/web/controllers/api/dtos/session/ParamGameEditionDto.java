package ua.kostenko.battleship.battleship.web.controllers.api.dtos.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParamGameEditionDto {
    private String gameEdition;
}
