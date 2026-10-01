package ua.kostenko.battleship.app.web;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.kostenko.battleship.app.config.BattleshipProperties;
import ua.kostenko.battleship.app.web.dto.Limits;
import ua.kostenko.battleship.app.web.dto.Meta;
import ua.kostenko.battleship.application.port.TimeSource;

/** Publishes the contract version and the limits the server enforces, read straight off the properties (R55). */
@RestController
class MetaController {
    /** The contract version this server implements; bumped by hand with {@code contracts/openapi.yaml} info.version. */
    static final String API_VERSION = "1.0.0";

    private final BattleshipProperties properties;
    private final TimeSource time;

    MetaController(BattleshipProperties properties, TimeSource time) {
        this.properties = properties;
        this.time = time;
    }

    @GetMapping("/api/v1/meta")
    Meta meta() {
        Limits limits = new Limits()
                .idleTimeoutSeconds(properties.idleTimeoutSeconds())
                .maxGameDurationSeconds(properties.maxGameDurationSeconds())
                .resultRetentionSeconds(properties.resultRetentionSeconds())
                .invitationLifetimeSeconds(properties.invitationLifetimeSeconds())
                .presenceIntervalSeconds(properties.presenceIntervalSeconds())
                .heartbeatSeconds(properties.heartbeatSeconds());
        return new Meta()
                .apiVersion(API_VERSION)
                .serverTime(OffsetDateTime.ofInstant(time.now(), ZoneOffset.UTC))
                .limits(limits);
    }
}
