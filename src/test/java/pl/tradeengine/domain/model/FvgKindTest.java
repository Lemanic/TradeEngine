package pl.tradeengine.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Jedyny mapper enumow z prawdziwymi galeziami. Kontrakt na drucie z Pine Script: signalType
 * przychodzi jako np. "FVG_BULLISH" / "IFVG_BEARISH".
 */
class FvgKindTest {

    @Test
    void shouldMapToFvg_whenSignalTypeStartsWithFvgPrefix() {
        assertThat(FvgKind.fromSignalType("FVG_BULLISH")).isEqualTo(FvgKind.FVG);
        assertThat(FvgKind.fromSignalType("FVG_BEARISH")).isEqualTo(FvgKind.FVG);
    }

    @Test
    void shouldMapToIfvg_whenSignalTypeStartsWithIfvgPrefix() {
        assertThat(FvgKind.fromSignalType("IFVG_BULLISH")).isEqualTo(FvgKind.IFVG);
    }

    @Test
    void shouldPreferIfvgPrefix_becauseItIsCheckedBeforeFvg() {
        // "IFVG_" zawiera "FVG", wiec kolejnosc sprawdzen w produkcji jest nosna, nie przypadkowa.
        assertThat(FvgKind.fromSignalType("IFVG_X")).isEqualTo(FvgKind.IFVG);
    }

    @Test
    void shouldThrow_whenPrefixHasNoUnderscore() {
        // Samo "FVG" bez podkreslnika nie przechodzi — to realny tryb awarii przy zmianie alertu
        // w Pine Script.
        assertThatThrownBy(() -> FvgKind.fromSignalType("FVG"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported signalType: FVG");
        assertThatThrownBy(() -> FvgKind.fromSignalType("IFVG"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrow_whenSignalTypeIsNullOrBlank() {
        assertThatThrownBy(() -> FvgKind.fromSignalType(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or blank");
        assertThatThrownBy(() -> FvgKind.fromSignalType("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or blank");
    }

    @Test
    void shouldThrow_whenSignalTypeIsUnknown() {
        assertThatThrownBy(() -> FvgKind.fromSignalType("ORDER_BLOCK_BULLISH"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ORDER_BLOCK_BULLISH");
    }
}
