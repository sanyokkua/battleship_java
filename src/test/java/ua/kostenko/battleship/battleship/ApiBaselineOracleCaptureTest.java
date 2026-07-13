package ua.kostenko.battleship.battleship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipDirection;
import ua.kostenko.battleship.battleship.web.api.dtos.ExceptionDto;
import ua.kostenko.battleship.battleship.web.api.dtos.ParamCoordinateDto;
import ua.kostenko.battleship.battleship.web.api.dtos.gameplay.ResponseGameplayStateDto;
import ua.kostenko.battleship.battleship.web.api.dtos.gameplay.ResponseShotResultDto;
import ua.kostenko.battleship.battleship.web.api.dtos.preparation.*;
import ua.kostenko.battleship.battleship.web.api.dtos.session.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PHASE-0.1 regression oracle capture test.
 * <p>
 * This test is NOT a conventional unit test. It drives one full, deterministic two-player game
 * against the CURRENT (pre-redesign) REST API on the UKRAINIAN edition, and for every HTTP call
 * dumps the request body, response body, and a small metadata file to
 * {@code docs/redesign/artifacts/api-baseline/}. A {@code manifest.json} listing every captured
 * step (in order) is written at the end.
 * </p>
 * <p>
 * Purpose: give later redesign phases (Phase 1 contract tests, Phase 8 frontend/back-end parity
 * checks) a concrete, byte-level example of current API behavior to diff against. This test also
 * carries real assertions so it functions as a regression check, not just a data dump.
 * </p>
 * <p>
 * Regenerate with: {@code mvn test -Dtest=ApiBaselineOracleCaptureTest}
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ApiBaselineOracleCaptureTest {

    private static final Path ARTIFACTS_DIR = Path.of("docs", "redesign", "artifacts", "api-baseline");
    private static final List<Placement> SHIP_LAYOUT = List.of(
            // 4 x PATROL_BOAT (size 1)
            new Placement("PATROL_BOAT_1", 6, 0, ShipDirection.HORIZONTAL),
            new Placement("PATROL_BOAT_2", 6, 2, ShipDirection.HORIZONTAL),
            new Placement("PATROL_BOAT_3", 8, 0, ShipDirection.HORIZONTAL),
            new Placement("PATROL_BOAT_4", 8, 2, ShipDirection.HORIZONTAL),
            // 3 x SUBMARINE (size 2)
            new Placement("SUBMARINE_1", 4, 0, ShipDirection.HORIZONTAL),
            new Placement("SUBMARINE_2", 4, 3, ShipDirection.HORIZONTAL),
            new Placement("SUBMARINE_3", 4, 6, ShipDirection.HORIZONTAL),
            // 2 x DESTROYER (size 3)
            new Placement("DESTROYER_1", 2, 0, ShipDirection.HORIZONTAL),
            new Placement("DESTROYER_2", 2, 5, ShipDirection.HORIZONTAL),
            // 1 x BATTLESHIP (size 4)
            new Placement("BATTLESHIP", 0, 0, ShipDirection.HORIZONTAL));
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final List<StepRecord> manifest = new ArrayList<>();
    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate restTemplate;
    /**
     * Single source of truth for the "NN" prefix of every captured file. Every HTTP call in this
     * test goes through {@link #call(String, HttpMethod, String, Object, Class)}, which pulls the
     * next number from here - so there is no possibility of two calls writing to the same
     * filename, regardless of how the surrounding game logic is structured or reordered.
     */
    private int stepCounter = 0;
    private String baseUrl;

    private static int expectedSizeFor(String shipTypeKey) {
        if (shipTypeKey.startsWith("BATTLESHIP")) {
            return 4;
        } else if (shipTypeKey.startsWith("DESTROYER")) {
            return 3;
        } else if (shipTypeKey.startsWith("SUBMARINE")) {
            return 2;
        } else {
            return 1;
        }
    }

    /**
     * Drives one full deterministic two-player game on the UKRAINIAN edition and captures every
     * request/response pair to disk. Single test method (rather than many small ones) so that the
     * manifest reflects one coherent, linear game session, matching how the real frontend would
     * drive the API end to end.
     */
    @Test
    void capture_full_game_oracle() throws IOException {
        baseUrl = "http://localhost:" + port;
        Files.createDirectories(ARTIFACTS_DIR);

        // ---- GET /api/v2/game/editions ----
        var editionsResponse = call("get-editions",
                HttpMethod.GET,
                "/api/v2/game/editions",
                null,
                ResponseAvailableGameEditionsDto.class);
        assertThat(editionsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(editionsResponse.getBody()).isNotNull();
        assertThat(editionsResponse.getBody()
                .getGameEditions()).contains(GameEdition.UKRAINIAN.name(),
                GameEdition.MILTON_BRADLEY.name());

        // ---- POST /api/v2/game/sessions ----
        var sessionResponse = call("create-session",
                HttpMethod.POST,
                "/api/v2/game/sessions",
                new ParamGameEditionDto(GameEdition.UKRAINIAN.name()),
                ResponseCreatedSessionIdDto.class);
        assertThat(sessionResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(sessionResponse.getBody()).isNotNull();
        var sessionId = sessionResponse.getBody()
                .getSessionId();
        assertThat(sessionId).isNotBlank();

        // ---- POST .../players x2 ----
        var player1Response = call("create-player-1",
                HttpMethod.POST,
                "/api/v2/game/sessions/%s/players".formatted(sessionId),
                new ParamPlayerNameDto("Player_1"),
                ResponseCreatedPlayerDto.class);
        assertThat(player1Response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var player1Id = player1Response.getBody()
                .getPlayerId();
        assertThat(player1Id).isNotBlank();

        var player2Response = call("create-player-2",
                HttpMethod.POST,
                "/api/v2/game/sessions/%s/players".formatted(sessionId),
                new ParamPlayerNameDto("Player_2"),
                ResponseCreatedPlayerDto.class);
        assertThat(player2Response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var player2Id = player2Response.getBody()
                .getPlayerId();
        assertThat(player2Id).isNotBlank();

        // ---- GET .../state (session game stage) ----
        var stateResponse = call("get-session-state",
                HttpMethod.GET,
                "/api/v2/game/sessions/%s/state".formatted(sessionId),
                null,
                ResponseCurrentGameStageDto.class);
        assertThat(stateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stateResponse.getBody()
                .getGameStage()).isNotBlank();

        // ---- GET .../changesTime ----
        var changesTimeResponse = call("get-changes-time",
                HttpMethod.GET,
                "/api/v2/game/sessions/%s/changesTime".formatted(sessionId),
                null,
                ResponseLastSessionChangeTimeDto.class);
        assertThat(changesTimeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changesTimeResponse.getBody()
                .getLastId()).isNotBlank();

        // ---- GET .../preparationState (player 1, before any ship placed) ----
        var prepStateResponse = call("get-preparation-state-p1-initial",
                HttpMethod.GET,
                "/api/v2/game/sessions/%s/players/%s/preparationState".formatted(sessionId,
                        player1Id),
                null,
                ResponsePreparationState.class);
        assertThat(prepStateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        var initialShips = prepStateResponse.getBody()
                .getShips();
        assertThat(initialShips).hasSize(10);

        // ---- PUT ships for player 1 (all 10 ships, deterministic layout) ----
        var player1ShipIds = placeAllShips(sessionId, player1Id, "p1");

        // ---- PUT ships for player 2 (same deterministic layout) ----
        placeAllShips(sessionId, player2Id, "p2");

        // ---- DELETE one already-placed ship for player 1, then PUT it back ----
        // Battleship (size 4) for player 1 is anchored at (0,0), HORIZONTAL.
        var deleteResponse = call("delete-ship-p1-battleship",
                HttpMethod.DELETE,
                "/api/v2/game/sessions/%s/players/%s/ships".formatted(sessionId, player1Id),
                new ParamCoordinateDto(0, 0),
                ResponseShipRemovedDto.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleteResponse.getBody()
                .isDeleted()).isTrue();

        var battleshipId = player1ShipIds.get("BATTLESHIP");
        var reAddBattleshipResponse = call("put-ship-p1-battleship-readd",
                HttpMethod.PUT,
                "/api/v2/game/sessions/%s/players/%s/ships/%s".formatted(sessionId,
                        player1Id,
                        battleshipId),
                new ParamShipDto(0, 0, ShipDirection.HORIZONTAL.name()),
                ResponseShipAddedDto.class);
        assertThat(reAddBattleshipResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reAddBattleshipResponse.getBody()
                .getShipId()).isEqualTo(battleshipId);

        // ---- POST .../start x2 ----
        var startP1Response = call("start-p1",
                HttpMethod.POST,
                "/api/v2/game/sessions/%s/players/%s/start".formatted(sessionId, player1Id),
                null,
                ResponsePlayerReady.class);
        assertThat(startP1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(startP1Response.getBody()
                .isReady()).isTrue();

        var startP2Response = call("start-p2",
                HttpMethod.POST,
                "/api/v2/game/sessions/%s/players/%s/start".formatted(sessionId, player2Id),
                null,
                ResponsePlayerReady.class);
        assertThat(startP2Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(startP2Response.getBody()
                .isReady()).isTrue();

        // ---- GET .../opponent ----
        var opponentResponse = call("get-opponent-p1",
                HttpMethod.GET,
                "/api/v2/game/sessions/%s/players/%s/opponent".formatted(sessionId, player1Id),
                null,
                ResponseOpponentInformationDto.class);
        assertThat(opponentResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(opponentResponse.getBody()
                .getPlayerName()).isEqualTo("Player_2");

        // ---- GET .../state (gameplay state, player 1) ----
        var gameplayStateResponse = call("get-gameplay-state-p1-initial",
                HttpMethod.GET,
                "/api/v2/game/sessions/%s/players/%s/state".formatted(sessionId,
                        player1Id),
                null,
                ResponseGameplayStateDto.class);
        assertThat(gameplayStateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(gameplayStateResponse.getBody()
                .isHasWinner()).isFalse();

        // ---- Shots: player 1 fires at player 2's board until player 2's fleet is destroyed ----
        // Player 2's fleet layout mirrors player 1's (see placeAllShips / SHIP_LAYOUT).
        //
        // IMPORTANT (engine turn rule, see GameImpl#updateGameState): a MISS passes the active
        // turn to the opponent; a HIT or DESTROYED lets the shooter go again. Shooting twice in a
        // row as the same player after a MISS throws IllegalStateException ("Player is not active
        // to make a shot"), which is NOT one of the typed exceptions ValidationExceptionHandler
        // catches, so it surfaces as a bare 500 - not a 400. That is real, current engine
        // behavior (frozen per the redesign ground rules), not a bug to fix here. So: player 1
        // deliberately misses first (turn passes to player 2), player 2 takes one harmless shot
        // that also misses (turn passes back to player 1), and only then does player 1 run the
        // uninterrupted HIT/DESTROYED sequence that sinks player 2's whole fleet.

        // Player 1 deliberate MISS at an empty cell (9,9 is not part of any ship or its
        // neighbour region) - turn passes to player 2.
        shotAndAssert(sessionId, player1Id, 9, 9, "MISS");

        // Player 2 takes the turn back with a harmless MISS on player 1's board (same empty
        // cell, since both players share the same ship layout) - turn passes back to player 1.
        shotAndAssert(sessionId, player2Id, 9, 9, "MISS");

        // Submarine A of player 2 occupies (4,0)-(4,1): first shot HIT, second shot DESTROYED.
        // Both HIT and DESTROYED keep the turn with player 1, so the rest of this sequence can
        // run uninterrupted.
        shotAndAssert(sessionId, player1Id, 4, 0, "HIT");
        shotAndAssert(sessionId, player1Id, 4, 1, "DESTROYED");

        // Sink the rest of player 2's fleet to finish the game.
        // Battleship (size 4): (0,0)-(0,3)
        shotAndAssert(sessionId, player1Id, 0, 0, "HIT");
        shotAndAssert(sessionId, player1Id, 0, 1, "HIT");
        shotAndAssert(sessionId, player1Id, 0, 2, "HIT");
        shotAndAssert(sessionId, player1Id, 0, 3, "DESTROYED");

        // Destroyer A (size 3): (2,0)-(2,2)
        shotAndAssert(sessionId, player1Id, 2, 0, "HIT");
        shotAndAssert(sessionId, player1Id, 2, 1, "HIT");
        shotAndAssert(sessionId, player1Id, 2, 2, "DESTROYED");

        // Destroyer B (size 3): (2,5)-(2,7)
        shotAndAssert(sessionId, player1Id, 2, 5, "HIT");
        shotAndAssert(sessionId, player1Id, 2, 6, "HIT");
        shotAndAssert(sessionId, player1Id, 2, 7, "DESTROYED");

        // Submarine B (size 2): (4,3)-(4,4)
        shotAndAssert(sessionId, player1Id, 4, 3, "HIT");
        shotAndAssert(sessionId, player1Id, 4, 4, "DESTROYED");

        // Submarine C (size 2): (4,6)-(4,7)
        shotAndAssert(sessionId, player1Id, 4, 6, "HIT");
        shotAndAssert(sessionId, player1Id, 4, 7, "DESTROYED");

        // PatrolBoat A (size 1): (6,0) - single-cell ship: one shot is both HIT and DESTROYED.
        shotAndAssert(sessionId, player1Id, 6, 0, "DESTROYED");

        // PatrolBoat B (size 1): (6,2)
        shotAndAssert(sessionId, player1Id, 6, 2, "DESTROYED");

        // PatrolBoat C (size 1): (8,0)
        shotAndAssert(sessionId, player1Id, 8, 0, "DESTROYED");

        // PatrolBoat D (size 1): (8,2) - last ship, sinking this ends the game.
        shotAndAssert(sessionId, player1Id, 8, 2, "DESTROYED");

        // ---- Final GET .../state for BOTH players, expecting hasWinner = true ----
        var finalStateP1 = call("get-gameplay-state-p1-final",
                HttpMethod.GET,
                "/api/v2/game/sessions/%s/players/%s/state".formatted(sessionId, player1Id),
                null,
                ResponseGameplayStateDto.class);
        assertThat(finalStateP1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(finalStateP1.getBody()
                .isHasWinner()).isTrue();
        assertThat(finalStateP1.getBody()
                .getWinnerPlayerName()).isEqualTo("Player_1");
        assertThat(finalStateP1.getBody()
                .isPlayerWinner()).isTrue();

        var finalStateP2 = call("get-gameplay-state-p2-final",
                HttpMethod.GET,
                "/api/v2/game/sessions/%s/players/%s/state".formatted(sessionId, player2Id),
                null,
                ResponseGameplayStateDto.class);
        assertThat(finalStateP2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(finalStateP2.getBody()
                .isHasWinner()).isTrue();
        assertThat(finalStateP2.getBody()
                .getWinnerPlayerName()).isEqualTo("Player_1");
        assertThat(finalStateP2.getBody()
                .isPlayerWinner()).isFalse();

        // ---- One deliberately invalid call: shot with out-of-bounds coordinates ----
        var invalidShotResponse = call("invalid-shot-out-of-bounds",
                HttpMethod.POST,
                "/api/v2/game/sessions/%s/players/%s/field/shot".formatted(sessionId,
                        player1Id),
                new ParamCoordinateDto(99, 99),
                ExceptionDto.class);
        assertThat(invalidShotResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidShotResponse.getBody()).isNotNull();
        assertThat(invalidShotResponse.getBody()
                .getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(invalidShotResponse.getBody()
                .getErrorMessage()).isNotBlank();

        // ---- Write manifest.json ----
        writeJson("manifest.json", manifest);

        assertThat(manifest).hasSizeGreaterThanOrEqualTo(12);
    }

    /**
     * Places all 10 ships for the given player, in ascending-size order (matching the order
     * returned by the preparationState endpoint), capturing a GET (preparation state) + PUT (add
     * ship) call pair for each. Returns a map from a stable ship-type key (e.g. "BATTLESHIP",
     * "SUBMARINE_1") to the server-assigned shipId, so the caller can re-address specific ships
     * later (e.g. for the delete/re-add step).
     */
    private Map<String, String> placeAllShips(String sessionId, String playerId, String playerSlug)
            throws IOException {
        var shipIdByKey = new LinkedHashMap<String, String>();
        for (var placement : SHIP_LAYOUT) {
            var prepState = call("get-preparation-state-%s-%s".formatted(playerSlug,
                            placement.shipTypeKey()
                                    .toLowerCase()),
                    HttpMethod.GET,
                    "/api/v2/game/sessions/%s/players/%s/preparationState".formatted(sessionId,
                            playerId),
                    null,
                    ResponsePreparationState.class);
            assertThat(prepState.getStatusCode()).isEqualTo(HttpStatus.OK);
            var remainingShips = prepState.getBody()
                    .getShips();
            assertThat(remainingShips).isNotEmpty();
            // Ships not yet placed are returned smallest-first; take the smallest remaining one
            // that matches this placement's expected size, keeping placement deterministic.
            var expectedSize = expectedSizeFor(placement.shipTypeKey());
            var shipToPlace = remainingShips.stream()
                    .filter(s -> s.getShipSize() == expectedSize)
                    .findFirst()
                    .orElseThrow();

            var putResponse = call("put-ship-%s-%s".formatted(playerSlug,
                            placement.shipTypeKey()
                                    .toLowerCase()),
                    HttpMethod.PUT,
                    "/api/v2/game/sessions/%s/players/%s/ships/%s".formatted(sessionId,
                            playerId,
                            shipToPlace.getShipId()),
                    new ParamShipDto(placement.row(), placement.col(), placement.direction()
                            .name()),
                    ResponseShipAddedDto.class);
            assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(putResponse.getBody()
                    .getShipId()).isEqualTo(shipToPlace.getShipId());

            shipIdByKey.put(placement.shipTypeKey(), shipToPlace.getShipId());
        }
        return shipIdByKey;
    }

    private void shotAndAssert(String sessionId, String shooterPlayerId, int row, int col, String expectedResult)
            throws IOException {
        var response = call("shot-%s-r%d-c%d".formatted(expectedResult.toLowerCase(), row, col),
                HttpMethod.POST,
                "/api/v2/game/sessions/%s/players/%s/field/shot".formatted(sessionId, shooterPlayerId),
                new ParamCoordinateDto(row, col),
                ResponseShotResultDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()
                .getShotResult()).isEqualTo(expectedResult);
    }

    /**
     * Executes an HTTP call via TestRestTemplate, dumps request/response/meta JSON files to
     * {@link #ARTIFACTS_DIR} under a zero-padded, monotonically increasing step prefix, records
     * the step in the in-memory manifest, and returns the typed response.
     */
    private <T> ResponseEntity<T> call(
            String slug, HttpMethod method, String path, Object requestBody, Class<T> responseType)
            throws IOException {
        stepCounter++;
        var finalSlug = "%02d-%s".formatted(stepCounter, slug);

        var url = baseUrl + path;
        @SuppressWarnings("unchecked") HttpEntity<Object> entity =
                requestBody == null ? (HttpEntity<Object>) HttpEntity.EMPTY : new HttpEntity<>(requestBody);

        var response = restTemplate.exchange(url, method, entity, responseType);

        if (requestBody != null) {
            writeJson(finalSlug + ".request.json", requestBody);
        }
        writeJson(finalSlug + ".response.json", response.getBody());

        var meta = new MetaRecord(method.name(), path, response.getStatusCode()
                .value());
        writeJson(finalSlug + ".meta.json", meta);

        manifest.add(new StepRecord(finalSlug, method.name(), path, response.getStatusCode()
                .value()));

        return response;
    }

    private void writeJson(String fileName, Object value) throws IOException {
        var json = objectMapper.writeValueAsString(value);
        Files.writeString(ARTIFACTS_DIR.resolve(fileName), json);
    }

    /**
     * Ship layout shared by both players: a set of (shipType, row, col, direction) placements
     * on the 10x10 UKRAINIAN board, chosen so that no two ships (or their 8-directional
     * neighbour cells) touch. Order matches ascending ship size, matching the order the
     * preparationState endpoint returns (see PreparationRestController#getPreparationState,
     * which sorts by shipSize ascending).
     */
    private record Placement(String shipTypeKey, int row, int col, ShipDirection direction) {
    }

    private record MetaRecord(String method, String path, int status) {
    }

    private record StepRecord(String slug, String method, String path, int status) {
    }
}
