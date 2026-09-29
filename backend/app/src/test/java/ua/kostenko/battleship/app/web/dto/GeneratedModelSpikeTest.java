package ua.kostenko.battleship.app.web.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.app.web.JSON;

class GeneratedModelSpikeTest {
    private final ObjectMapper mapper = new JSON().getMapper();

    @Test
    void emitsExpectedComponentClassesEnumsAndInlineAliases() throws Exception {
        List<String> objectSchemas = List.of(
                "Coordinate",
                "Ship",
                "Board",
                "Player",
                "Shot",
                "Outcome",
                "DurationAggregate",
                "FleetSummary",
                "PlayerStatistics",
                "MatchStatistics",
                "GameStatistics",
                "GameSnapshot",
                "FleetEntry",
                "Ruleset",
                "RulesetList",
                "Limits",
                "Meta",
                "Health",
                "CreateGameRequest",
                "JoinGameRequest",
                "PlaceShipCommand",
                "RemoveShipCommand",
                "FireCommand",
                "SimpleCommand",
                "Command",
                "CommandRequest",
                "StreamClosed",
                "Violation",
                "Problem");
        List<String> enumSchemas =
                List.of("Orientation", "Side", "Phase", "Action", "CellState", "ShipStatus", "ProblemCode");

        for (String name : objectSchemas) {
            assertNotNull(Class.forName("ua.kostenko.battleship.app.web.dto." + name));
        }
        for (String name : enumSchemas) {
            assertTrue(
                    Class.forName("ua.kostenko.battleship.app.web.dto." + name).isEnum(), name);
        }
        for (String alias : List.of("GameId", "DisplayName", "Instant")) {
            assertThrows(
                    ClassNotFoundException.class, () -> Class.forName("ua.kostenko.battleship.app.web.dto." + alias));
        }
        assertEquals(
                OffsetDateTime.class,
                GameSnapshot.class.getMethod("getServerTime").getReturnType());
    }

    @Test
    void deserializesAllCommandDiscriminatorTags() throws Exception {
        assertTrue(
                mapper.readValue(
                                        "{\"type\":\"PLACE_SHIP\",\"shipId\":\"s01\",\"anchor\":{\"rowIndex\":0,\"columnIndex\":0},\"orientation\":\"HORIZONTAL\"}",
                                        Command.class)
                                .getActualInstance()
                        instanceof PlaceShipCommand);
        assertTrue(
                mapper.readValue("{\"type\":\"REMOVE_SHIP\",\"shipId\":\"s01\"}", Command.class)
                                .getActualInstance()
                        instanceof RemoveShipCommand);
        assertTrue(
                mapper.readValue("{\"type\":\"FIRE\",\"target\":{\"rowIndex\":0,\"columnIndex\":0}}", Command.class)
                                .getActualInstance()
                        instanceof FireCommand);
        for (String type : List.of("PLACE_FLEET_RANDOMLY", "CLEAR_FLEET", "READY", "RESIGN")) {
            Command command = mapper.readValue("{\"type\":\"" + type + "\"}", Command.class);
            assertTrue(command.getActualInstance() instanceof SimpleCommand, type);
        }
    }

    @Test
    void problemCodeEnumRoundTripsAllContractWireValues() throws Exception {
        List<String> values = List.of(
                "malformed-request",
                "session-required",
                "request-security-rejected",
                "game-unavailable",
                "invitation-unavailable",
                "action-not-allowed",
                "placement-out-of-bounds",
                "placement-overlap",
                "placement-touching",
                "target-already-fired",
                "game-expired",
                "payload-too-large",
                "unsupported-media-type",
                "validation-failed",
                "rate-limit-exceeded",
                "internal-error",
                "service-unavailable");
        for (String value : values) {
            ProblemCode code = mapper.readValue("\"" + value + "\"", ProblemCode.class);
            assertEquals(value, mapper.readTree(mapper.writeValueAsString(code)).textValue());
        }
    }

    @Test
    void instantAliasUsesOffsetDateTimeAndSerializesUtcMilliseconds() throws Exception {
        OffsetDateTime time = OffsetDateTime.parse("2026-09-20T12:00:00Z");
        GameSnapshot snapshot = new GameSnapshot().serverTime(time);
        mapper.configOverride(OffsetDateTime.class)
                .setFormat(JsonFormat.Value.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
        assertEquals(
                "2026-09-20T12:00:00.000Z",
                mapper.readTree(mapper.writeValueAsString(snapshot))
                        .get("serverTime")
                        .asText());
        assertEquals(OffsetDateTime.class, snapshot.getServerTime().getClass());
    }

    @Test
    void oneElementAllOfSchemasInlineAndRulesetBoardIsUsable() throws Exception {
        assertEquals(Coordinate.class, Ship.class.getMethod("getAnchor").getReturnType());
        assertEquals(Orientation.class, Ship.class.getMethod("getOrientation").getReturnType());
        assertEquals(
                DurationAggregate.class,
                PlayerStatistics.class.getMethod("getTurns").getReturnType());
        assertEquals(
                DurationAggregate.class,
                PlayerStatistics.class.getMethod("getShotDecisions").getReturnType());
        assertEquals(
                OffsetDateTime.class,
                GameSnapshot.class.getMethod("getServerTime").getReturnType());
        assertEquals(Player.class, GameSnapshot.class.getMethod("getOpponent").getReturnType());
        assertEquals(Side.class, GameSnapshot.class.getMethod("getTurn").getReturnType());
        assertEquals(
                OffsetDateTime.class,
                GameSnapshot.class.getMethod("getExpiresAt").getReturnType());
        assertEquals(
                OffsetDateTime.class,
                GameSnapshot.class.getMethod("getInvitationExpiresAt").getReturnType());
        assertEquals(Board.class, GameSnapshot.class.getMethod("getYourBoard").getReturnType());
        assertEquals(
                Board.class, GameSnapshot.class.getMethod("getOpponentBoard").getReturnType());
        assertEquals(Outcome.class, GameSnapshot.class.getMethod("getOutcome").getReturnType());
        assertEquals(
                GameStatistics.class,
                GameSnapshot.class.getMethod("getStatistics").getReturnType());
        assertNotNull(Class.forName("ua.kostenko.battleship.app.web.dto.StreamClosed"));
        assertNotNull(Class.forName("ua.kostenko.battleship.app.web.JSON"));
        assertNotNull(Class.forName("ua.kostenko.battleship.app.web.RFC3339DateFormat"));
        assertNotNull(Class.forName("ua.kostenko.battleship.app.web.dto.AbstractOpenApiSchema"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("ua.kostenko.battleship.app.web.ApiClient"));

        Class<?> rulesetBoard = Ruleset.class.getMethod("getBoard").getReturnType();
        assertFalse(rulesetBoard.equals(Object.class));
        assertEquals(Integer.class, rulesetBoard.getMethod("getRows").getReturnType());
        assertEquals(Integer.class, rulesetBoard.getMethod("getColumns").getReturnType());
    }
}
