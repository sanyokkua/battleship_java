package ua.kostenko.battleship.battleship.web.controllers.api.dtos.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseAvailableGameEditionsDto {
    private List<String> gameEditions;
}
