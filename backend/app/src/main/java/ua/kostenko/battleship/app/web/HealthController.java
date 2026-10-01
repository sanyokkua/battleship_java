package ua.kostenko.battleship.app.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.kostenko.battleship.app.config.GracefulShutdownConfig;
import ua.kostenko.battleship.app.web.dto.Health;

/** Hand-written health probe; its 503 form carries a {@link Health} body, not a problem document (research D20). */
@RestController
class HealthController {
    private final GracefulShutdownConfig shutdown;

    HealthController(GracefulShutdownConfig shutdown) {
        this.shutdown = shutdown;
    }

    @GetMapping("/api/v1/health")
    ResponseEntity<Health> health() {
        if (shutdown.ready()) return ResponseEntity.ok(new Health().live(true).ready(true));
        return notReady(shutdown.draining() ? Health.ReasonEnum.DRAINING : Health.ReasonEnum.STARTING);
    }

    private static ResponseEntity<Health> notReady(Health.ReasonEnum reason) {
        return ResponseEntity.status(503)
                .body(new Health().live(true).ready(false).reason(reason));
    }
}
