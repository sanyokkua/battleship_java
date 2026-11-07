package ua.kostenko.battleship.domain.model;

import java.text.Normalizer;

/** Normalizes and validates player supplied display names. */
public final class DisplayNameNormalizer {
    private static final int MIN_CODE_POINTS = 1;
    private static final int MAX_CODE_POINTS = 32;
    private static final int ZERO_WIDTH_JOINER = 0x200D;

    private DisplayNameNormalizer() {}

    /** Trims surrounding whitespace, normalizes to NFC, and validates a display name. */
    public static String normalize(String name) {
        if (name == null) {
            throw new InvalidNameException(InvalidNameReason.REQUIRED);
        }
        rejectInvisibleCharacters(name);
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

    /**
     * Control, format (zero-width and bidirectional marks), line and paragraph separators and unassigned code points.
     * The zero-width joiner is allowed because emoji sequences need it.
     */
    private static void rejectInvisibleCharacters(String name) {
        if (name.codePoints().anyMatch(DisplayNameNormalizer::isInvisible)) {
            throw new InvalidNameException(InvalidNameReason.INVALID_FORMAT);
        }
    }

    private static boolean isInvisible(int codePoint) {
        if (codePoint == ZERO_WIDTH_JOINER) {
            return false;
        }
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL,
                    Character.FORMAT,
                    Character.LINE_SEPARATOR,
                    Character.PARAGRAPH_SEPARATOR,
                    Character.UNASSIGNED -> true;
            default -> false;
        };
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
