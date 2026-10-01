package ua.kostenko.battleship.app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.kostenko.battleship.app.security.SecurityHttp.issuedSession;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import ua.kostenko.battleship.app.security.SecurityHttp;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;

/**
 * One "browser" for the journey ITs: its own cookie state, and the contract's operations as calls that answer the
 * caller's current snapshot. It keeps no game state — a client is only a view (spec.md R01).
 */
public final class Browser {
    private static final String GAMES = "/api/v1/games/";

    private final int port;
    private final SecurityHttp http;
    private String sessionCookie;
    private String gameId;

    public Browser(int port) {
        this.port = port;
        this.http = new SecurityHttp(port);
    }

    /** A new browser holding only this one's session cookie, as after a page reload (S3). */
    public Browser reloaded() {
        Browser fresh = new Browser(port);
        fresh.sessionCookie = sessionCookie;
        fresh.gameId = gameId;
        return fresh;
    }

    public String gameId() {
        return gameId;
    }

    public SecurityHttp http() {
        return http;
    }

    public static String invitationSecret(JsonNode snapshot) {
        String url = snapshot.get("invitationUrl").asText();
        return url.substring(url.indexOf("#invite=") + "#invite=".length());
    }

    public JsonNode create(String rulesetId, String displayName) throws Exception {
        Reply created = http.postJson(
                "/api/v1/games", null, "{\"rulesetId\":\"" + rulesetId + "\",\"displayName\":\"" + displayName + "\"}");
        assertThat(created.status()).isEqualTo(201);
        sessionCookie = issuedSession(created);
        gameId = created.json().get("gameId").asText();
        return created.json();
    }

    public JsonNode join(String gameId, String invitationSecret, String displayName) throws Exception {
        Reply joined = http.postJson(
                GAMES + gameId + "/join",
                null,
                "{\"invitationSecret\":\"" + invitationSecret + "\",\"displayName\":\"" + displayName + "\"}");
        assertThat(joined.status()).isEqualTo(200);
        sessionCookie = issuedSession(joined);
        this.gameId = gameId;
        return joined.json();
    }

    /** The one {@code getGame} read; the status is asserted by callers that expect anything but 200. */
    public Reply read() throws Exception {
        return http.call("GET", GAMES + gameId, sessionCookie);
    }

    public JsonNode snapshot() throws Exception {
        Reply reply = read();
        assertThat(reply.status()).isEqualTo(200);
        return reply.json();
    }

    public Reply send(String command) throws Exception {
        return http.postJson(
                GAMES + gameId + "/commands",
                sessionCookie,
                "{\"commandId\":\"" + UUID.randomUUID() + "\",\"command\":" + command + "}");
    }

    /** Sends a command that must be accepted and returns the caller's resulting snapshot. */
    public JsonNode accept(String command) throws Exception {
        Reply reply = send(command);
        assertThat(reply.status())
                .as(command + " -> " + reply.response().body())
                .isEqualTo(200);
        return reply.json();
    }

    public Reply leave() throws Exception {
        return http.postWithToken(GAMES + gameId + "/leave", http.freshToken(), sessionCookie);
    }

    public static String simple(String type) {
        return "{\"type\":\"" + type + "\"}";
    }

    public static String fire(int row, int column) {
        return "{\"type\":\"FIRE\",\"target\":{\"rowIndex\":" + row + ",\"columnIndex\":" + column + "}}";
    }

    public static String placeShip(String shipId, int row, int column, String orientation) {
        return "{\"type\":\"PLACE_SHIP\",\"shipId\":\"" + shipId + "\",\"anchor\":{\"rowIndex\":" + row
                + ",\"columnIndex\":" + column + "},\"orientation\":\"" + orientation + "\"}";
    }
}
