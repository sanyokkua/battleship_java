package ua.kostenko.battleship.application.result;

/** A framework-free refusal to be mapped to the contract's Problem response by the HTTP adapter. */
public final class ApplicationFailure extends RuntimeException {
    private final String code;
    private final String field;
    private final String rule;
    private final Integer retryAfterSeconds;

    public ApplicationFailure(String code, String field, String rule, Integer retryAfterSeconds) {
        super(code);
        this.code = code;
        this.field = field;
        this.rule = rule;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String code() {
        return code;
    }

    public String field() {
        return field;
    }

    public String rule() {
        return rule;
    }

    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
