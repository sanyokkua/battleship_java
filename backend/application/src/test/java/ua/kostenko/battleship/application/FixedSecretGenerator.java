package ua.kostenko.battleship.application;

import java.util.Objects;
import ua.kostenko.battleship.application.port.SecretGenerator;

public final class FixedSecretGenerator implements SecretGenerator {
    private final String gameId;
    private final String sessionValue;
    private final String invitationSecret;

    public FixedSecretGenerator(String gameId, String sessionValue, String invitationSecret) {
        this.gameId = Objects.requireNonNull(gameId, "gameId");
        this.sessionValue = Objects.requireNonNull(sessionValue, "sessionValue");
        this.invitationSecret = Objects.requireNonNull(invitationSecret, "invitationSecret");
    }

    @Override
    public String gameId() {
        return gameId;
    }

    @Override
    public String sessionValue() {
        return sessionValue;
    }

    @Override
    public String invitationSecret() {
        return invitationSecret;
    }
}
