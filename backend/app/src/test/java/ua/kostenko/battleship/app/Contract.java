package ua.kostenko.battleship.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * {@code contracts/openapi.yaml} read as data, so the conformance proof compares the running service with the
 * published contract itself and not with a second copy of it: its embedded examples, its schemas, and every enum it
 * lists. The oracle has two halves. {@link #problems} walks a response body against its contract schema (required
 * present, optional present exactly per the condition the contract states, nothing the schema does not define, types,
 * formats, patterns) and records every enum value it meets. {@link #differences} compares a body with an embedded
 * example: same keys and types at every level, same array sizes, and exact values for the pinned fields.
 */
final class Contract {
    private static final String SCHEMAS = "/components/schemas/";
    private static final Pattern MILLIS_UTC = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Contract INSTANCE = load();

    /** Enum values seen on the wire, by contract label ({@code Phase}, {@code Shot.result}, ...). */
    private static final Map<String, Set<String>> EMITTED = new ConcurrentHashMap<>();

    private final JsonNode root;

    private Contract(JsonNode root) {
        this.root = root;
    }

    static Contract get() {
        return INSTANCE;
    }

    private static Contract load() {
        Path file = Path.of("..", "..", "contracts", "openapi.yaml");
        try {
            Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file));
            return new Contract(JSON.valueToTree(parsed));
        } catch (IOException unreadable) {
            throw new IllegalStateException("cannot read " + file.toAbsolutePath(), unreadable);
        }
    }

    // ------------------------------------------------------------------ the contract's own data

    JsonNode example(String name) {
        return required(root.at("/components/examples/" + name + "/value"), "example " + name);
    }

    /** The names of every example under {@code components.examples}. */
    List<String> exampleNames() {
        List<String> names = new ArrayList<>();
        root.at("/components/examples").fieldNames().forEachRemaining(names::add);
        return names;
    }

    /** The names of the failure components under {@code components.responses}. */
    List<String> problemResponses() {
        List<String> names = new ArrayList<>();
        root.at("/components/responses").fieldNames().forEachRemaining(names::add);
        return names;
    }

    JsonNode schema(String name) {
        return required(root.at(SCHEMAS + name), "schema " + name);
    }

    /** The embedded example of a failure component, for instance {@code Conflict} and {@code placementTouching}. */
    JsonNode problemExample(String response, String exampleName) {
        return required(
                root.at("/components/responses/" + response + "/content/application~1problem+json/examples/"
                        + exampleName + "/value"),
                "example " + response + "." + exampleName);
    }

    /** The names of the embedded examples of a failure component, in the contract's order. */
    List<String> problemExampleNames(String response) {
        List<String> names = new ArrayList<>();
        root.at("/components/responses/" + response + "/content/application~1problem+json/examples")
                .fieldNames()
                .forEachRemaining(names::add);
        return names;
    }

    /** The one embedded example of an operation's response, at a JSON pointer below {@code /paths}. */
    JsonNode pathExample(String pointer) {
        return required(root.at("/paths/" + pointer), "path example " + pointer);
    }

    private static JsonNode required(JsonNode node, String what) {
        if (node.isMissingNode()) throw new IllegalStateException("the contract has no " + what);
        return node;
    }

    /** The frames of the {@code EventStream} example: each is the list of its lines. */
    List<List<String>> eventStreamFrames() {
        List<List<String>> frames = new ArrayList<>();
        for (String frame : example("EventStream").asText().split("\n\\s*\n")) {
            List<String> lines =
                    frame.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
            if (!lines.isEmpty()) frames.add(lines);
        }
        return frames;
    }

    /** Every enum the contract lists, by label: a named enum schema, or {@code Schema.property} for an inline one. */
    Map<String, List<String>> enums() {
        Map<String, List<String>> all = new TreeMap<>();
        root.at("/components/schemas").fields().forEachRemaining(entry -> {
            if (entry.getValue().has("enum")) {
                all.put(entry.getKey(), strings(entry.getValue().get("enum")));
            }
            entry.getValue().path("properties").fields().forEachRemaining(property -> {
                if (property.getValue().has("enum")) {
                    all.put(
                            entry.getKey() + "." + property.getKey(),
                            strings(property.getValue().get("enum")));
                }
            });
        });
        return all;
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    // ------------------------------------------------------------------ enum values seen on the wire

    static Map<String, Set<String>> emitted() {
        Map<String, Set<String>> copy = new TreeMap<>();
        EMITTED.forEach((label, values) -> copy.put(label, new TreeSet<>(values)));
        return copy;
    }

    // ------------------------------------------------------------------ the schema walk

    /**
     * What is wrong with {@code body} as a document of the named schema; empty when it conforms. Enum values are
     * recorded, not judged: listing them is the enum proof's job.
     */
    List<String> problems(JsonNode body, String schemaName) {
        return problems(body, schemaName, EMITTED);
    }

    /** As {@link #problems(JsonNode, String)} for a body that is not an emission, such as a contract example. */
    List<String> problemsOfExample(JsonNode example, String schemaName) {
        return problems(example, schemaName, new ConcurrentHashMap<>());
    }

    private List<String> problems(JsonNode body, String schemaName, Map<String, Set<String>> sink) {
        List<String> problems = new ArrayList<>();
        walk(body, schema(schemaName), schemaName, "", problems, sink);
        if (schemaName.equals("GameSnapshot")) snapshotConditions(body, problems);
        if (schemaName.equals("Problem")) problemConditions(body, problems);
        if (schemaName.equals("Health")) healthConditions(body, problems);
        return problems;
    }

    /**
     * What is wrong with the optional fields of {@code node} against {@code schemaName}: with {@code all} set, every
     * property the schema defines must be present at every level; otherwise only the required ones may be.
     */
    List<String> completeness(JsonNode node, String schemaName, boolean all) {
        List<String> problems = new ArrayList<>();
        completeness(node, schema(schemaName), "", all, problems);
        return problems;
    }

    private void completeness(JsonNode node, JsonNode schema, String at, boolean all, List<String> problems) {
        schema = resolve(schema);
        if (node.isObject() && schema.has("properties")) {
            Set<String> expected = new TreeSet<>();
            JsonNode required = schema.path("required");
            if (all) {
                schema.get("properties").fieldNames().forEachRemaining(expected::add);
            } else {
                required.forEach(name -> expected.add(name.asText()));
            }
            Set<String> actual = new TreeSet<>();
            node.fields().forEachRemaining(field -> {
                if (field.getValue().isNull()) {
                    problems.add(label(at + "/" + field.getKey()) + " is an explicit null");
                } else {
                    actual.add(field.getKey());
                }
            });
            if (!actual.equals(expected)) {
                problems.add(label(at) + " carries " + actual + " but the contract's " + (all ? "full" : "required")
                        + " set is " + expected);
            }
            for (String name : actual) {
                if (schema.get("properties").has(name)) {
                    completeness(node.get(name), schema.get("properties").get(name), at + "/" + name, all, problems);
                }
            }
        } else if (node.isArray() && schema.has("items")) {
            for (int i = 0; i < node.size(); i++) {
                completeness(node.get(i), schema.get("items"), at + "/" + i, all, problems);
            }
        }
    }

    private void walk(
            JsonNode value,
            JsonNode declared,
            String enumLabel,
            String at,
            List<String> problems,
            Map<String, Set<String>> sink) {
        if (value.isNull()) {
            problems.add(label(at) + " is an explicit null, and the contract defines no nullable field");
            return;
        }
        JsonNode schema = resolve(declared);
        String label = declared.has("$ref") ? refName(declared) : enumLabel;
        if (declared.has("allOf")
                && declared.get("allOf").size() == 1
                && declared.get("allOf").get(0).has("$ref")) {
            label = refName(declared.get("allOf").get(0));
        }
        String type = schema.path("type").asText("");
        switch (type) {
            case "object" -> object(value, schema, label, at, problems, sink);
            case "array" -> array(value, schema, label, at, problems, sink);
            case "string" -> string(value, schema, label, at, problems, sink);
            case "integer" -> {
                if (!value.isIntegralNumber()) {
                    problems.add(label(at) + " must be an integer, was " + value);
                } else {
                    range(value, schema, at, problems);
                }
            }
            case "number" -> {
                if (!value.isNumber()) {
                    problems.add(label(at) + " must be a number, was " + value);
                } else {
                    range(value, schema, at, problems);
                }
            }
            case "boolean" -> {
                if (!value.isBoolean()) problems.add(label(at) + " must be a boolean, was " + value);
            }
            default -> {}
        }
    }

    private void object(
            JsonNode value,
            JsonNode schema,
            String label,
            String at,
            List<String> problems,
            Map<String, Set<String>> sink) {
        if (!value.isObject()) {
            problems.add(label(at) + " must be an object, was " + value.getNodeType());
            return;
        }
        JsonNode properties = schema.path("properties");
        for (JsonNode name : schema.path("required")) {
            if (!value.hasNonNull(name.asText())) {
                problems.add(label(at + "/" + name.asText()) + " is required by " + label + " and is absent");
            }
        }
        value.fieldNames().forEachRemaining(name -> {
            if (!properties.has(name)) {
                problems.add(label(at + "/" + name) + " is not defined by " + label);
            }
        });
        properties.fields().forEachRemaining(property -> {
            if (value.has(property.getKey())) {
                walk(
                        value.get(property.getKey()),
                        property.getValue(),
                        label + "." + property.getKey(),
                        at + "/" + property.getKey(),
                        problems,
                        sink);
            }
        });
    }

    private void array(
            JsonNode value,
            JsonNode schema,
            String label,
            String at,
            List<String> problems,
            Map<String, Set<String>> sink) {
        if (!value.isArray()) {
            problems.add(label(at) + " must be an array, was " + value.getNodeType());
            return;
        }
        if (schema.path("uniqueItems").asBoolean(false)) {
            Set<JsonNode> seen = new LinkedHashSet<>();
            value.forEach(item -> {
                if (!seen.add(item)) problems.add(label(at) + " repeats " + item);
            });
        }
        if (schema.has("minItems") && value.size() < schema.get("minItems").asInt()) {
            problems.add(label(at) + " has fewer than " + schema.get("minItems").asInt() + " items");
        }
        for (int i = 0; i < value.size(); i++) {
            walk(value.get(i), schema.get("items"), label + "[]", at + "/" + i, problems, sink);
        }
    }

    private void string(
            JsonNode value,
            JsonNode schema,
            String label,
            String at,
            List<String> problems,
            Map<String, Set<String>> sink) {
        if (!value.isTextual()) {
            problems.add(label(at) + " must be a string, was " + value);
            return;
        }
        String text = value.asText();
        if (schema.has("enum"))
            sink.computeIfAbsent(label, k -> ConcurrentHashMap.newKeySet()).add(text);
        if (schema.has("pattern")
                && !Pattern.compile(schema.get("pattern").asText())
                        .matcher(text)
                        .find()) {
            problems.add(label(at) + " does not match " + schema.get("pattern").asText() + ": " + text);
        }
        int length = text.codePointCount(0, text.length());
        if (schema.has("minLength") && length < schema.get("minLength").asInt()) {
            problems.add(
                    label(at) + " is shorter than " + schema.get("minLength").asInt());
        }
        if (schema.has("maxLength") && length > schema.get("maxLength").asInt()) {
            problems.add(
                    label(at) + " is longer than " + schema.get("maxLength").asInt());
        }
        switch (schema.path("format").asText("")) {
            case "date-time" -> {
                if (!isUtcInstant(text)) problems.add(label(at) + " is not an RFC 3339 UTC timestamp: " + text);
            }
            case "uri" -> {
                try {
                    if (!URI.create(text).isAbsolute()) problems.add(label(at) + " is not an absolute URI: " + text);
                } catch (IllegalArgumentException malformed) {
                    problems.add(label(at) + " is not a URI: " + text);
                }
            }
            default -> {}
        }
    }

    /** Every timestamp the contract shows, the {@code Instant} schema's own example included, is UTC with millis. */
    private static boolean isUtcInstant(String text) {
        try {
            return OffsetDateTime.parse(text).getOffset().equals(ZoneOffset.UTC)
                    && MILLIS_UTC.matcher(text).matches();
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private static void range(JsonNode value, JsonNode schema, String at, List<String> problems) {
        if (schema.has("minimum") && value.asDouble() < schema.get("minimum").asDouble()) {
            problems.add(label(at) + " is below " + schema.get("minimum") + ": " + value);
        }
        if (schema.has("maximum") && value.asDouble() > schema.get("maximum").asDouble()) {
            problems.add(label(at) + " is above " + schema.get("maximum") + ": " + value);
        }
    }

    private JsonNode resolve(JsonNode schema) {
        if (schema.has("$ref"))
            return resolve(root.at(schema.get("$ref").asText().substring(1)));
        if (schema.has("allOf") && schema.get("allOf").size() == 1)
            return resolve(schema.get("allOf").get(0));
        return schema;
    }

    private static String refName(JsonNode ref) {
        String pointer = ref.get("$ref").asText();
        return pointer.substring(pointer.lastIndexOf('/') + 1);
    }

    private static String label(String at) {
        return at.isEmpty() ? "the body" : at;
    }

    // ------------------------------------------------------------------ the conditions the contract states in prose

    private static void snapshotConditions(JsonNode s, List<String> problems) {
        String phase = s.path("phase").asText();
        presence(s, "opponent", !phase.equals("WAITING"), "a guest has joined (phase is not WAITING)", problems);
        presence(s, "turn", phase.equals("PLAYING"), "phase is PLAYING", problems);
        presence(s, "invitationUrl", phase.equals("WAITING"), "host in WAITING", problems);
        presence(s, "invitationExpiresAt", phase.equals("WAITING"), "host in WAITING", problems);
        presence(s, "outcome", phase.equals("FINISHED"), "phase is FINISHED", problems);
        presence(s, "statistics", phase.equals("FINISHED"), "phase is FINISHED", problems);
        if ((phase.equals("WAITING") || phase.equals("PLACEMENT")) && s.has("lastShot")) {
            problems.add("lastShot is present before any shot could be fired");
        }
        if (s.has("lastShot")) {
            presence(
                    s.get("lastShot"),
                    "sunkShipId",
                    s.at("/lastShot/result").asText().equals("SUNK"),
                    "result is SUNK",
                    problems);
        }
        for (String board : List.of("yourBoard", "opponentBoard")) {
            for (JsonNode ship : s.at("/" + board + "/ships")) {
                boolean placed = !ship.path("cells").isEmpty();
                presence(ship, "anchor", placed, "the ship is placed", problems);
                presence(ship, "orientation", placed, "the ship is placed", problems);
            }
        }
        for (JsonNode row : s.at("/yourBoard/grid")) {
            row.forEach(cell -> {
                if (cell.asText().equals("UNKNOWN")) problems.add("yourBoard.grid contains UNKNOWN");
            });
        }
        if (!phase.equals("FINISHED")) {
            Set<String> disclosed = Set.of("UNKNOWN", "MISS", "HIT", "SUNK", "REVEALED_WATER");
            for (JsonNode row : s.at("/opponentBoard/grid")) {
                row.forEach(cell -> {
                    if (!disclosed.contains(cell.asText())) {
                        problems.add("opponentBoard.grid shows " + cell.asText() + " before FINISHED");
                    }
                });
            }
        }
        for (String side : List.of("you", "opponent")) {
            JsonNode player = s.at("/statistics/" + side);
            if (player.isMissingNode()) continue;
            presence(player, "accuracy", player.path("shots").asInt() > 0, "shots is above 0", problems);
            for (String aggregate : List.of("turns", "shotDecisions")) {
                for (String field : List.of("averageMs", "fastestMs", "slowestMs")) {
                    presence(
                            player.path(aggregate),
                            field,
                            player.path(aggregate).path("count").asInt() > 0,
                            "count is above 0",
                            problems);
                }
            }
        }
    }

    private static void problemConditions(JsonNode p, List<String> problems) {
        String code = p.path("code").asText();
        presence(p, "violations", code.equals("validation-failed"), "code is validation-failed", problems);
        presence(
                p,
                "retryAfterSeconds",
                code.equals("rate-limit-exceeded") || code.equals("service-unavailable"),
                "code is rate-limit-exceeded or service-unavailable",
                problems);
    }

    private static void healthConditions(JsonNode h, List<String> problems) {
        presence(h, "reason", !h.path("ready").asBoolean(), "ready is false", problems);
    }

    /** An optional field is present exactly when its stated condition holds. */
    private static void presence(JsonNode object, String field, boolean when, String why, List<String> problems) {
        if (object.has(field) != when) {
            problems.add(field + " must be " + (when ? "present" : "absent") + " when "
                    + (when ? why : "not (" + why + ")"));
        }
    }

    // ------------------------------------------------------------------ comparison with an embedded example

    /**
     * How {@code live} differs from the embedded {@code example}: keys and JSON types at every level, array sizes
     * (except ship {@code cells}, whose length follows the ship), and the exact value at every pinned JSON pointer.
     * Everything else, such as ids, clocks, board contents and statistics numbers, is volatile and judged by
     * {@link #problems} instead.
     */
    static List<String> differences(JsonNode live, JsonNode example, Set<String> pinned) {
        List<String> differences = new ArrayList<>();
        compare(live, example, "", pinned, differences);
        for (String pointer : pinned) {
            JsonNode a = live.at(pointer);
            JsonNode b = example.at(pointer);
            if (!a.equals(b)) differences.add(pointer + " is " + a + " but the example pins " + b);
        }
        return differences;
    }

    private static void compare(JsonNode live, JsonNode example, String at, Set<String> pinned, List<String> out) {
        if (live.getNodeType() != example.getNodeType() || live.isIntegralNumber() != example.isIntegralNumber()) {
            out.add(label(at) + " is " + live.getNodeType() + " but the example has " + example.getNodeType());
            return;
        }
        if (live.isObject()) {
            Set<String> liveKeys = new TreeSet<>();
            live.fieldNames().forEachRemaining(liveKeys::add);
            Set<String> exampleKeys = new TreeSet<>();
            example.fieldNames().forEachRemaining(exampleKeys::add);
            if (!liveKeys.equals(exampleKeys)) {
                Set<String> missing = new TreeSet<>(exampleKeys);
                missing.removeAll(liveKeys);
                Set<String> extra = new TreeSet<>(liveKeys);
                extra.removeAll(exampleKeys);
                out.add(label(at) + " lacks " + missing + " and adds " + extra + " compared with the example");
            }
            for (String key : liveKeys) {
                if (exampleKeys.contains(key)) compare(live.get(key), example.get(key), at + "/" + key, pinned, out);
            }
        } else if (live.isArray()) {
            if (at.endsWith("/cells")) {
                if (live.isEmpty() != example.isEmpty()) {
                    out.add(label(at) + " is " + (live.isEmpty() ? "empty" : "filled") + " but the example's is not");
                } else if (!live.isEmpty()) {
                    live.forEach(cell -> compare(cell, example.get(0), at + "/*", pinned, out));
                }
                return;
            }
            if (live.size() != example.size()) {
                out.add(label(at) + " has " + live.size() + " items but the example has " + example.size());
                return;
            }
            for (int i = 0; i < live.size(); i++) compare(live.get(i), example.get(i), at + "/" + i, pinned, out);
        }
    }

    /** A copy of {@code node} without the named top-level fields; for the byte-level comparisons. */
    static JsonNode without(JsonNode node, String... fields) {
        ObjectNode copy = ((ObjectNode) node).deepCopy();
        copy.remove(List.of(fields));
        return copy;
    }
}
