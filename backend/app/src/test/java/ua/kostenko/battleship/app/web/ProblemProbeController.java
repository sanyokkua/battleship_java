package ua.kostenko.battleship.app.web;

import jakarta.validation.Valid;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ua.kostenko.battleship.app.web.dto.CommandRequest;
import ua.kostenko.battleship.app.web.dto.CreateGameRequest;
import ua.kostenko.battleship.app.web.dto.JoinGameRequest;
import ua.kostenko.battleship.application.result.ApplicationFailure;

/**
 * Test fixture that supplies throw sites and body-binding paths for {@link ProblemMappingIT}; the advice, mapper and
 * filters under test are the real ones.
 */
@TestConfiguration(proxyBeanMethods = false)
@RestController
class ProblemProbeController {
    @GetMapping("/probe/ok")
    ResponseEntity<Void> ok() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/probe/fail/{code}")
    void fail(@PathVariable String code, @RequestParam(required = false) Integer retryAfter) {
        throw new ApplicationFailure(code, null, null, retryAfter);
    }

    @GetMapping("/probe/validation-failed")
    void validationFailed() {
        throw new ApplicationFailure("validation-failed", "/displayName", "TOO_LONG", null);
    }

    @GetMapping("/probe/bad-rule")
    void badRule() {
        throw new ApplicationFailure("validation-failed", "/x", "NOT_A_RULE", null);
    }

    @GetMapping("/probe/boom")
    void boom(@RequestParam String leak) {
        throw new IllegalStateException("leaked " + leak);
    }

    @PostMapping("/probe/create-game")
    ResponseEntity<Void> createGame(@Valid @RequestBody CreateGameRequest body) {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/probe/join")
    ResponseEntity<Void> join(@Valid @RequestBody JoinGameRequest body) {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/probe/join-and-fail")
    void joinAndFail(@Valid @RequestBody JoinGameRequest body) {
        throw new IllegalStateException("leaked " + body.getInvitationSecret());
    }

    @PostMapping("/probe/command")
    ResponseEntity<Void> command(@Valid @RequestBody CommandRequest body) {
        return ResponseEntity.ok().build();
    }
}
