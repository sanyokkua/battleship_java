package ua.kostenko.battleship.battleship.web.api.dtos.gameplay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseShotResultDto {
    private String shotResult;
}
