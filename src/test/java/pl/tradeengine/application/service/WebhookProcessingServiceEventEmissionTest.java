package pl.tradeengine.application.service;

import org.junit.jupiter.api.Test;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.application.dto.PriceUpdateDto;
import pl.tradeengine.domain.event.FvgFilledEvent;
import pl.tradeengine.domain.event.FvgTouchedEvent;
import pl.tradeengine.domain.event.PriceCandleEvent;
import pl.tradeengine.domain.model.AlertMode;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.Timeframe;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ktore zdarzenia handlePriceUpdate faktycznie wypuszcza.
 *
 * <p>Osobna klasa od WebhookProcessingServiceHandlePriceUpdateTest, bo tamta zaslepia
 * ScenarioEngine mockiem i nie widzi, KTORE zdarzenie poszlo w swiat — a strict stubs Mockito nie
 * pozwalaja trzymac obu podejsc w jednej klasie (zaslepka bylaby nieuzyta w tych testach).
 * Tutaj idzie prawdziwy lancuch z {@link WebhookChainHarness}, a zdarzenia czyta sie z
 * RecordingScenario.
 */
class WebhookProcessingServiceEventEmissionTest {

    private static final BigDecimal LOWER = new BigDecimal("60000");
    private static final BigDecimal UPPER = new BigDecimal("61000");

    @Test
    void shouldAlwaysEmitPriceCandleEvent_evenWhenNoFvgMatched() {
        WebhookChainHarness h = WebhookChainHarness.silent();

        h.service.handlePriceUpdate(TestFixtures.priceUpdate(Timeframe.H1,
                new BigDecimal("50500"), new BigDecimal("50000"), new BigDecimal("50200")));

        // Zadnej strefy w repozytorium, a swieca i tak jedzie do scenariuszy — na tym stoja
        // wszystkie wskazniki liczone ze strumienia cen.
        assertThat(h.scenario.allOfType(PriceCandleEvent.class)).hasSize(1);
        PriceCandleEvent event = h.scenario.firstOfType(PriceCandleEvent.class);
        assertThat(event.candle().high()).isEqualByComparingTo("50500");
        assertThat(event.candle().low()).isEqualByComparingTo("50000");
        assertThat(event.candle().close()).isEqualByComparingTo("50200");
        // open to zaslepka BigDecimal(69), a openTime/closeTime sa null — PriceUpdateDto ich nie niesie.
        assertThat(event.candle().open()).isEqualByComparingTo("69");
        assertThat(event.candle().openTime()).isNull();
    }

    @Test
    void shouldEmitTouchedEventWithoutTouchedAtOrAlertMode_becauseTheNineArgConstructorResetsThem() {
        WebhookChainHarness h = WebhookChainHarness.silent();
        TestFixtures.saveFvg(h.fvgRepo, Timeframe.H1, Direction.LONG, LOWER, UPPER, FvgStatus.CREATED);

        h.service.handlePriceUpdate(TestFixtures.priceUpdate(Timeframe.H1,
                new BigDecimal("60800"), new BigDecimal("60200"), new BigDecimal("60500")));

        FvgTouchedEvent event = h.scenario.firstOfType(FvgTouchedEvent.class);
        assertThat(event.fvgZone().getStatus()).isEqualTo(FvgStatus.TOUCHED);
        assertThat(event.touchedAt()).isNotNull();
        // Repozytorium ma touchedAt, ale strefa w zdarzeniu jest budowana od nowa 9-argumentowym
        // konstruktorem, ktory zeruje touchedAt/filledAt/alertMode. GrinderStrategyScenario liczy
        // z tego "timeDiffMinutes" i dostaje -1, czyli brak osi czasu w opisie alertu.
        assertThat(h.fvgRepo.findById(1L).orElseThrow().getTouchedAt()).isNotNull();
        assertThat(event.fvgZone().getTouchedAt()).isNull();
        assertThat(event.fvgZone().getAlertMode()).isEqualTo(AlertMode.OFF);
    }

    @Test
    void shouldEmitFilledEventWithoutFilledAt_forTheSameReason() {
        WebhookChainHarness h = WebhookChainHarness.silent();
        TestFixtures.saveFvg(h.fvgRepo, Timeframe.H1, Direction.LONG, LOWER, UPPER, FvgStatus.CREATED);

        h.service.handlePriceUpdate(TestFixtures.priceUpdate(Timeframe.H1,
                new BigDecimal("60500"), new BigDecimal("59900"), new BigDecimal("60000")));

        FvgFilledEvent event = h.scenario.firstOfType(FvgFilledEvent.class);
        assertThat(event.fvgZone().getStatus()).isEqualTo(FvgStatus.FILLED);
        assertThat(event.filledAt()).isNotNull();
        assertThat(event.fvgZone().getFilledAt()).isNull();
        assertThat(event.fvgZone().getExpiresAt()).isNull();
        // Wypelnienie wyklucza dotkniecie na tej samej swiecy — galezie sa rozlaczne.
        assertThat(h.scenario.allOfType(FvgTouchedEvent.class)).isEmpty();
    }

    @Test
    void shouldNotEmitTouchedEventTwice_whenZoneIsAlreadyTouched() {
        WebhookChainHarness h = WebhookChainHarness.silent();
        TestFixtures.saveFvg(h.fvgRepo, Timeframe.H1, Direction.LONG, LOWER, UPPER, FvgStatus.TOUCHED);

        PriceUpdateDto inZone = TestFixtures.priceUpdate(Timeframe.H1,
                new BigDecimal("60800"), new BigDecimal("60200"), new BigDecimal("60500"));
        h.service.handlePriceUpdate(inZone);
        h.service.handlePriceUpdate(inZone);

        // markTouched jest w galezi `status == CREATED`, wiec dotkniecie jest jednorazowe —
        // inaczej kazda swieca w strefie generowalaby alert.
        assertThat(h.scenario.allOfType(FvgTouchedEvent.class)).isEmpty();
        assertThat(h.scenario.allOfType(PriceCandleEvent.class)).hasSize(2);
    }
}
