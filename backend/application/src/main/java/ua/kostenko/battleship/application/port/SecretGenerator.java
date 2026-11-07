package ua.kostenko.battleship.application.port;

public interface SecretGenerator {
    String gameId();

    String sessionValue();

    String invitationSecret();
}
