package pl.tradeengine.domain.util;

import org.junit.jupiter.api.Test;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Timeframe;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.tradeengine.TestFixtures.BTC;

/**
 * hlc3 to jedyne wejscie WaveTrendIndicator, wiec kazdy blad zaokraglenia tutaj przesuwa
 * wszystkie crossy w backtescie.
 */
class PriceCandleUtilsTest {

    private static PriceCandle candle(String high, String low, String close) {
        return new PriceCandle(BTC, Timeframe.H1, null, null,
                new BigDecimal("60000"), new BigDecimal(high), new BigDecimal(low), new BigDecimal(close));
    }

    @Test
    void shouldAverageHighLowAndClose_atScaleEight() {
        BigDecimal hlc3 = PriceCandleUtils.hlc3(candle("61000", "60000", "60500"));

        assertThat(hlc3).isEqualByComparingTo("60500");
        assertThat(hlc3.scale()).isEqualTo(8);
    }

    @Test
    void shouldRoundHalfUp_atTheEighthDecimalPlace() {
        // (1 + 1 + 1.00000002) / 3 = 1.000000006666... -> HALF_UP na 8. miejscu -> 1.00000001
        assertThat(PriceCandleUtils.hlc3(candle("1", "1", "1.00000002")))
                .isEqualByComparingTo("1.00000001");
    }

    @Test
    void shouldNotThrowOnNonTerminatingDivision_becauseScaleIsFixed() {
        // Bez podanej skali BigDecimal.divide rzucilby ArithmeticException na 100/3.
        assertThat(PriceCandleUtils.hlc3(candle("100", "100", "100")))
                .isEqualByComparingTo("100");
        assertThat(PriceCandleUtils.hlc3(candle("101", "100", "100")))
                .isEqualByComparingTo("100.33333333");
    }

    @Test
    void shouldThrowNullPointer_whenCandleIsNull() {
        // Brak strazy w produkcji — utrwalone swiadomie, zeby zmiana byla widoczna.
        assertThatThrownBy(() -> PriceCandleUtils.hlc3(null))
                .isInstanceOf(NullPointerException.class);
    }
}
