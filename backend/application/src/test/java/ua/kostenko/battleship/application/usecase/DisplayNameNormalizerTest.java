package ua.kostenko.battleship.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.domain.model.DisplayNameNormalizer;

class DisplayNameNormalizerTest {
    @Test
    void trimsSurroundingWhitespace() {
        assertThat(DisplayNameNormalizer.normalize("  Ada Lovelace  ")).isEqualTo("Ada Lovelace");
    }

    @Test
    void normalizesToNfcBeforeReturningTheName() {
        assertThat(DisplayNameNormalizer.normalize("Cafe\u0301")).isEqualTo("Caf\u00e9");
    }

    @Test
    void countsThirtyTwoDecomposedAccentedLettersAfterNfcNormalization() {
        assertThat(DisplayNameNormalizer.normalize("e\u0301".repeat(32))).isEqualTo("\u00e9".repeat(32));
    }

    @Test
    void acceptsThirtyTwoCodePointsIncludingAstralCharacters() {
        assertThat(DisplayNameNormalizer.normalize("😀".repeat(32))).hasSize(64);
    }

    @Test
    void rejectsThirtyThreeCodePoints() {
        assertThatThrownBy(() -> DisplayNameNormalizer.normalize("a".repeat(33)))
                .isInstanceOf(DisplayNameNormalizer.InvalidNameException.class)
                .extracting("reason")
                .hasToString("TOO_LONG");
    }

    @Test
    void rejectsNamesEmptyAfterTrimming() {
        assertThatThrownBy(() -> DisplayNameNormalizer.normalize("   "))
                .isInstanceOf(DisplayNameNormalizer.InvalidNameException.class)
                .extracting("reason")
                .hasToString("TOO_SHORT");
    }

    @Test
    void rejectsMissingNameWithRequiredReason() {
        assertThatThrownBy(() -> DisplayNameNormalizer.normalize(null))
                .isInstanceOf(DisplayNameNormalizer.InvalidNameException.class)
                .extracting("reason")
                .hasToString("REQUIRED");
    }

    @Test
    void rejectsControlCharactersWithTheirContractReason() {
        for (String control : new String[] {"\u0000", "\u001f", "\u007f"}) {
            assertThatThrownBy(() -> DisplayNameNormalizer.normalize("Ada" + control))
                    .isInstanceOf(DisplayNameNormalizer.InvalidNameException.class)
                    .extracting("reason")
                    .hasToString("INVALID_FORMAT");
        }
    }
}
