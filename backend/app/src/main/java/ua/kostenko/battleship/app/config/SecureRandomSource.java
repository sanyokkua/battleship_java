package ua.kostenko.battleship.app.config;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;
import ua.kostenko.battleship.domain.RandomSource;

@Component
public final class SecureRandomSource implements RandomSource {
    private final SecureRandom random = new SecureRandom();

    @Override
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }
}
