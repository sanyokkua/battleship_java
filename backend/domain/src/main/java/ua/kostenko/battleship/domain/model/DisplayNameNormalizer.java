package ua.kostenko.battleship.domain.model;

import java.text.Normalizer;

/** Normalizes and validates player supplied display names. */
public final class DisplayNameNormalizer {
    private static final int MIN_CODE_POINTS = 1;
    private static final int MAX_CODE_POINTS = 32;

    private DisplayNameNormalizer() {}

    /** Trims surrounding whitespace, normalizes to NFC, and validates a display name. */
    public static String normalize(String name) {
        if (name == null) {
            throw new InvalidNameException(InvalidNameReason.REQUIRED);
        }
        rejectControlCharacters(name);
        String normalized = Normalizer.normalize(name.strip(), Normalizer.Form.NFC);

        int codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount < MIN_CODE_POINTS) {
            throw new InvalidNameException(InvalidNameReason.TOO_SHORT);
        }
        if (codePointCount > MAX_CODE_POINTS) {
            throw new InvalidNameException(InvalidNameReason.TOO_LONG);
        }
        return normalized;
    }

    private static void rejectControlCharacters(String name) {
        if (name.codePoints().anyMatch(codePoint -> Character.getType(codePoint) == Character.CONTROL)) {
            throw new InvalidNameException(InvalidNameReason.INVALID_FORMAT);
        }
    }

    public enum InvalidNameReason {
        REQUIRED,
        INVALID_FORMAT,
        TOO_SHORT,
        TOO_LONG
    }

    /** Domain-local signal that also identifies the contract validation rule. */
    public static final class InvalidNameException extends IllegalArgumentException {
        private final InvalidNameReason reason;

        private InvalidNameException(InvalidNameReason reason) {
            super(reason.name());
            this.reason = reason;
        }

        public InvalidNameReason reason() {
            return reason;
        }
    }
}
