package pl.tradeengine.domain.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wyjscie tej metody jest czescia opisu kazdego alertu (buildAlert/generateAlert we wszystkich
 * scenariuszach), a opis jest tym, co AlertToSend.toString() wysyla na Telegram. Czyli te piec
 * asercji pilnuje tresci wiadomosci, nie formatowania dla samego formatowania.
 */
class PriceFormatterTest {

    @Test
    void shouldReturnNotAvailable_whenPriceIsNull() {
        assertThat(PriceFormatter.format(null)).isEqualTo("N/A");
    }

    @Test
    void shouldStripTrailingZeros_whenScaleCarriesThem() {
        assertThat(PriceFormatter.format(new BigDecimal("60000.10"))).isEqualTo("60000.1");
    }

    @Test
    void shouldExpandScientificNotation_insteadOfLeakingItIntoTheAlert() {
        // toPlainString, nie toString — inaczej w wiadomosci wyladowaloby "1E+3".
        assertThat(PriceFormatter.format(new BigDecimal("1E+3"))).isEqualTo("1000");
    }

    @Test
    void shouldCollapseZeroToSingleDigit_whateverTheScale() {
        assertThat(PriceFormatter.format(new BigDecimal("0.00"))).isEqualTo("0");
    }

    @Test
    void shouldKeepTheMinusSign_whenPriceIsNegative() {
        assertThat(PriceFormatter.format(new BigDecimal("-60000.500"))).isEqualTo("-60000.5");
    }
}
