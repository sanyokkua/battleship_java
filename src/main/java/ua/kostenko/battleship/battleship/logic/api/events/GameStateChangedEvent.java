package ua.kostenko.battleship.battleship.logic.api.events;

/**
 * Published whenever a mutating {@link ua.kostenko.battleship.battleship.logic.api.GameControllerApi}
 * operation durably completes for a session, after the session's lock has been released.
 * <p>
 * Listeners (e.g. the SSE broadcaster) use this purely as a "something changed, go look" signal;
 * it intentionally carries no state of its own so producers never need to keep it in sync with
 * whatever data a listener eventually pushes.
 * </p>
 *
 * @param sessionId the ID of the game session whose state changed
 */
public record GameStateChangedEvent(String sessionId) {
}
