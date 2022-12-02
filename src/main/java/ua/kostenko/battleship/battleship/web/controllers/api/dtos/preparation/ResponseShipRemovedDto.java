package ua.kostenko.battleship.battleship.web.controllers.api.dtos.preparation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseShipRemovedDto {
    private boolean deleted;

    public static ResponseShipRemovedDto fromString(String value) {
        return new ResponseShipRemovedDto(StringUtils.isNotBlank(value));
    }
}
