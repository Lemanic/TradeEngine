package pl.tradeengine.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.application.dto.FvgAlertDto;
import pl.tradeengine.domain.event.FvgCreatedEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgKind;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.Timeframe;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.tradeengine.TestFixtures.BTC;

/**
 * handleFvg nie ma wlasnych galezi, ale wola prywatny mapDtoToFvgZone — a tam siedza cztery
 * rzucajace mappery, kazdy z innym slownikiem drutu z Pine Script. To one decyduja, czy webhook
 * skonczy sie 202 czy 400.
 */
class WebhookProcessingServiceHandleFvgTest {

    private WebhookChainHarness h;

    @BeforeEach
    void setUp() {
        h = WebhookChainHarness.silent();
    }

    // ─────────────────────────────────────────────────────────────
    // Mapowanie DTO -> FvgZone
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldMapEveryFieldFromPayload_whenSavingTheZone() {
        FvgAlertDto dto = TestFixtures.fvgAlert(Timeframe.H4, "SHORT", "FVG_BEARISH", "CREATED");

        h.service.handleFvg(dto);

        FvgZone saved = h.fvgRepo.findById(1L).orElseThrow();
        assertThat(saved.getSymbol()).isEqualTo(BTC);
        assertThat(saved.getTimeframe()).isEqualTo(Timeframe.H4);
        assertThat(saved.getDirection()).isEqualTo(Direction.SHORT);
        assertThat(saved.getKind()).isEqualTo(FvgKind.FVG);
        assertThat(saved.getStatus()).isEqualTo(FvgStatus.CREATED);
        assertThat(saved.getLowerPrice()).isEqualByComparingTo("60000");
        assertThat(saved.getUpperPrice()).isEqualByComparingTo("61000");
    }

    @Test
    void shouldLeaveStrengthNull_becauseItIsNotComputedOnThisPath() {
        // mapDtoToFvgZone przekazuje null jako strength. Kto to odboksuje, dostanie NPE —
        // dlatego warto, zeby bylo to widoczne w tescie, a nie tylko w kodzie.
        h.service.handleFvg(TestFixtures.fvgAlert(Timeframe.H1, Direction.LONG));

        assertThat(h.fvgRepo.findById(1L).orElseThrow().getStrength()).isNull();
    }

    @Test
    void shouldMapIfvgPrefix_toIfvgKind() {
        h.service.handleFvg(TestFixtures.fvgAlert(Timeframe.H1, "LONG", "IFVG_BULLISH", "CREATED"));

        assertThat(h.fvgRepo.findById(1L).orElseThrow().getKind()).isEqualTo(FvgKind.IFVG);
    }

    // ─────────────────────────────────────────────────────────────
    // Zdarzenie i dyspozycja alertow
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldEmitFvgCreatedEventCarryingTheSavedZone_notTheMappedOne() {
        // Serwis emituje wynik save(), czyli strefe z nadanym id. Scenariusze loguja to id i
        // odpytuja po nim repozytorium, wiec emisja strefy bez id bylaby cicha regresja.
        h.service.handleFvg(TestFixtures.fvgAlert(Timeframe.H1, Direction.LONG));

        FvgCreatedEvent event = h.scenario.firstOfType(FvgCreatedEvent.class);
        assertThat(event.fvgZone().getId()).isNotNull().isEqualTo(1L);
        assertThat(event.fvgZone().getSymbol()).isEqualTo(BTC);
    }

    @Test
    void shouldDispatchAlertsFromScenario_allTheWayToThePublisher() {
        AlertToSend alert = TestFixtures.alert(WebhookChainHarness.SCENARIO_NAME);
        WebhookChainHarness loud = WebhookChainHarness.returningAlerts(alert);

        loud.service.handleFvg(TestFixtures.fvgAlert(Timeframe.H1, Direction.LONG));

        assertThat(loud.publisher.allAlerts()).containsExactly(alert);
    }

    // ─────────────────────────────────────────────────────────────
    // Zle payloady — to one koncza sie 400 w TradingViewWebhookController
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldThrowAndSaveNothing_whenTimeframeIsUnknown() {
        FvgAlertDto dto = new FvgAlertDto(BTC.code(), "M30", "FVG_BULLISH", "LONG",
                new BigDecimal("60000"), new BigDecimal("61000"), "CREATED");

        assertThatThrownBy(() -> h.service.handleFvg(dto)).isInstanceOf(IllegalArgumentException.class);

        assertThat(h.fvgRepo.findById(1L)).isEmpty();
        assertThat(h.scenario.seen()).isEmpty();
    }

    @Test
    void shouldThrow_whenDirectionOrStatusIsOutsideTheEnum() {
        // direction: Direction.fromSignal; fvgStatus: goly FvgStatus.valueOf — oba rzuca IAE,
        // wiec z punktu widzenia TradingView nie da sie ich rozroznic (oba to 400 bez ciala).
        assertThatThrownBy(() -> h.service.handleFvg(
                TestFixtures.fvgAlert(Timeframe.H1, "BULLISH", "FVG_BULLISH", "CREATED")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> h.service.handleFvg(
                TestFixtures.fvgAlert(Timeframe.H1, "LONG", "FVG_BULLISH", "OPEN")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> h.service.handleFvg(
                TestFixtures.fvgAlert(Timeframe.H1, "LONG", "ORDER_BLOCK", "CREATED")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
