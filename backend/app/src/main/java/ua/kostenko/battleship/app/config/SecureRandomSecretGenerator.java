package ua.kostenko.battleship.app.config;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;
import ua.kostenko.battleship.application.port.SecretGenerator;

@Component
public final class SecureRandomSecretGenerator implements SecretGenerator {
    private final SecureRandom random = new SecureRandom();

    @Override
    public String gameId() {
        return encodeRandomBytes(16);
    }

    @Override
    public String sessionValue() {
        return encodeRandomBytes(32);
    }

    @Override
    public String invitationSecret() {
        return encodeRandomBytes(32);
    }

    private String encodeRandomBytes(int byteCount) {
        byte[] bytes = new byte[byteCount];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
