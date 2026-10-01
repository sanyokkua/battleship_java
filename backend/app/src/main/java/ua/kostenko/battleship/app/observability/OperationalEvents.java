package ua.kostenko.battleship.app.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * The four operational events of Constitution V's EARS rule, each one diagnostic record: a rejected command, a game
 * expiring, state lost on restart, a realtime delivery failure. A record names the event, the correlation id and the
 * {@code gameId} (which grants nothing) and at most a problem code; never a name, cookie, secret, board, body or
 * address (R58). The caller of each event has already given the user a safe outcome.
 */
public class OperationalEvents {
    private static final Logger LOG = LoggerFactory.getLogger(OperationalEvents.class);

    /** A command the rules or the game's state refused; the request's own {@code gameId} is already in MDC. */
    public void commandRejected(String problemCode) {
        emit("command-rejected", "command rejected", null, problemCode);
    }

    /** A game that expired unfinished and has now been forgotten by the sweep. */
    public void gameExpired(String gameId) {
        emit("game-expired", "game expired and was reclaimed", gameId, null);
    }

    /** Said once at start-up: the service keeps no state across a restart, so nothing was recovered. */
    public void stateLostOnRestart() {
        emit(
                "state-lost-on-restart",
                "started with no state: nothing was recovered, any earlier game is gone",
                null,
                null);
    }

    /** A stream whose write failed, which is then ended; the game itself is unaffected. */
    public void deliveryFailed(String gameId) {
        emit("realtime-delivery-failed", "realtime delivery failed, stream ended", gameId, null);
    }

    private static void emit(String event, String message, String gameId, String problemCode) {
        CorrelationIdFilter.scoped(() -> {
            boolean ownGameId = gameId != null && MDC.get(CorrelationIdFilter.GAME_ID_KEY) == null;
            if (ownGameId) {
                MDC.put(CorrelationIdFilter.GAME_ID_KEY, gameId);
            }
            try {
                var record = LOG.atInfo().addKeyValue("event", event);
                if (problemCode != null) {
                    record = record.addKeyValue("code", problemCode);
                }
                record.log(message);
            } finally {
                if (ownGameId) {
                    MDC.remove(CorrelationIdFilter.GAME_ID_KEY);
                }
            }
        });
    }
}
