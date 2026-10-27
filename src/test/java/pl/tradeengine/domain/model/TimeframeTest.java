package pl.tradeengine.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeframeTest {

    @Test
    void shouldMapEveryCode_thatThePineScriptContractSends() {
        for (Timeframe tf : Timeframe.values()) {
            assertThat(Timeframe.fromCode(tf.name())).isEqualTo(tf);
        }
    }

    @Test
    void shouldThrow_whenCodeIsLowercase() {
        // fromCode to goly valueOf, wiec jest wrazliwy na wielkosc liter — inaczej niz
        // Direction.fromSignal, ktory podnosi do wielkich.
        assertThatThrownBy(() -> Timeframe.fromCode("h1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrow_whenCodeIsUnknown() {
        // To jest dokladnie ta sciezka, ktora TradingViewWebhookController zamienia na 400.
        assertThatThrownBy(() -> Timeframe.fromCode("M30"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Timeframe.fromCode(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldExposeFixedDurations_withW1AsSevenDays() {
        // W1 to Duration.ofDays(7), bez semantyki kalendarzowej. Dzieki temu wszystkie lookbacki
        // liczone jako duration * N swiec sa dokladne i niezalezne od stref czasowych.
        assertThat(Timeframe.M1.getDuration()).isEqualTo(Duration.ofMinutes(1));
        assertThat(Timeframe.M15.getDuration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(Timeframe.H4.getDuration()).isEqualTo(Duration.ofHours(4));
        assertThat(Timeframe.H8.getDuration()).isEqualTo(Duration.ofHours(8));
        assertThat(Timeframe.D1.getDuration()).isEqualTo(Duration.ofDays(1));
        assertThat(Timeframe.W1.getDuration()).isEqualTo(Duration.ofDays(7));

        // Konsekwencja dla FvgLtfConfirmationScenario: okno 40 swiec na M1 to rowne 40 minut.
        assertThat(Timeframe.M1.getDuration().multipliedBy(40)).isEqualTo(Duration.ofMinutes(40));
    }
}
