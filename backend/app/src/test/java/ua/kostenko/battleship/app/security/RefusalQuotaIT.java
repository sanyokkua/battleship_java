package ua.kostenko.battleship.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;

/** A request refused for its size or its site never spends rate-limit quota: those filters run before the limit. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "battleship.rate-limit.create-game-per-minute=2")
class RefusalQuotaIT {
    private static final String CREATE_BODY = "{\"rulesetId\":\"sea-battle-10-ship.v1\",\"displayName\":\"Captain\"}";

    @LocalServerPort
    private int port;

    @Test
    void oversizedAndCrossSiteRefusalsCostNoQuota() throws Exception {
        SecurityHttp http = new SecurityHttp(port);
        for (int i = 0; i < 5; i++) {
            assertThat(http.postJson("/api/v1/games", null, "x".repeat(20000)).status())
                    .isEqualTo(413);
            assertThat(http.postJson("/api/v1/games", null, CREATE_BODY, "Origin", "https://evil.example")
                            .status())
                    .isEqualTo(403);
        }

        Reply first = http.postJson("/api/v1/games", null, CREATE_BODY);
        Reply second = http.postJson("/api/v1/games", null, CREATE_BODY);
        Reply third = http.postJson("/api/v1/games", null, CREATE_BODY);

        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);
        assertThat(third.status()).isEqualTo(429);
    }
}
