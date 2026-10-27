package pl.tradeengine.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectionTest {

    @Test
    void shouldMapBothDirections_fromUppercaseSignal() {
        assertThat(Direction.fromSignal("LONG")).isEqualTo(Direction.LONG);
        assertThat(Direction.fromSignal("SHORT")).isEqualTo(Direction.SHORT);
    }

    @Test
    void shouldAcceptAnyCase_becauseTheMapperUppercasesFirst() {
        assertThat(Direction.fromSignal("long")).isEqualTo(Direction.LONG);
        assertThat(Direction.fromSignal("Short")).isEqualTo(Direction.SHORT);
    }

    @Test
    void shouldThrow_whenGivenMomentumVocabularyInsteadOfDirection() {
        // BULLISH/BEARISH to slownik MomentumAlertDto i BiasStatus, nie Direction. Pomylenie
        // tych dwoch slownikow to realny blad przy dodawaniu alertu w Pine Script.
        assertThatThrownBy(() -> Direction.fromSignal("BULLISH"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Direction.fromSignal("BUY"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowNullPointer_whenValueIsNull() {
        // toUpperCase() na nullu, nie IllegalArgumentException — rozne niz w Timeframe.fromCode.
        assertThatThrownBy(() -> Direction.fromSignal(null))
                .isInstanceOf(NullPointerException.class);
    }
}
