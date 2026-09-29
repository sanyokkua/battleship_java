package ua.kostenko.battleship.domain.transition;

import java.util.Objects;

public record Rejection(ProblemCode code, String field, String rule) {
    public Rejection {
        Objects.requireNonNull(code, "code");
        if (code == ProblemCode.VALIDATION_FAILED) {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(rule, "rule");
        } else if (field != null || rule != null) {
            throw new IllegalArgumentException("only validation failures carry a field and rule");
        }
    }

    public static Rejection validation(String field, String rule) {
        return new Rejection(ProblemCode.VALIDATION_FAILED, field, rule);
    }

    public static Rejection problem(ProblemCode code) {
        return new Rejection(code, null, null);
    }

    public enum ProblemCode {
        ACTION_NOT_ALLOWED("action-not-allowed"),
        VALIDATION_FAILED("validation-failed"),
        PLACEMENT_OUT_OF_BOUNDS("placement-out-of-bounds"),
        PLACEMENT_OVERLAP("placement-overlap"),
        PLACEMENT_TOUCHING("placement-touching"),
        RANDOM_ARRANGEMENT_FAILED("random-arrangement-failed");

        private final String wireCode;

        ProblemCode(String wireCode) {
            this.wireCode = wireCode;
        }

        public String wireCode() {
            return wireCode;
        }
    }
}
