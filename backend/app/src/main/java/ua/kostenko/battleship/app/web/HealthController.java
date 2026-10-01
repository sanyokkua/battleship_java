package ua.kostenko.battleship.app.web;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.kostenko.battleship.app.web.dto.Health;

/** Hand-written health probe; its 503 form carries a {@link Health} body, not a problem document (research D20). */
@RestController
class HealthController {
    private final ApplicationAvailability availability;

    HealthController(ApplicationAvailability availability) {
        this.availability = availability;
    }

    @GetMapping("/api/v1/health")
    ResponseEntity<Health> health() {
        if (availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC) {
            return ResponseEntity.ok(new Health().live(true).ready(true));
        }
        return ResponseEntity.status(503)
                .body(new Health().live(true).ready(false).reason(Health.ReasonEnum.STARTING));
    }
}
