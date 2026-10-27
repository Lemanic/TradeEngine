package pl.tradeengine.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.application.dto.DivergenceAlertDto;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.tradeengine.TestFixtures.BTC;

class WebhookProcessingServiceHandleDivergenceTest {

    private WebhookChainHarness h;

    @BeforeEach
    void setUp() {
        h = WebhookChainHarness.silent();
    }

    @Test
    void shouldMapSymbolTimeframeAndDirection_whenSavingTheSignal() {
        h.service.handleDivergence(TestFixtures.divergenceAlert(Timeframe.M15, "short"));

        DivergenceSignal saved = h.divergenceRepo.findAll().get(0);
        assertThat(saved.getSymbol()).isEqualTo(BTC);
        assertThat(saved.getTimeframe()).isEqualTo(Timeframe.M15);
        assertThat(saved.getDirection()).isEqualTo(Direction.SHORT);
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void shouldStoreConstantStrength_becauseTheScoringIsStillAStub() {
        // calculateStrengthBasedOnContext zwraca na twardo 21.37 i ignoruje payload. Test jest tu
        // po to, zeby wprowadzenie prawdziwego scoringu bylo widoczna zmiana, nie cicha.
        h.service.handleDivergence(TestFixtures.divergenceAlert(Timeframe.H1, "LONG"));

        assertThat(h.divergenceRepo.findAll().get(0).getStrength()).isEqualTo(21.37);
    }

    @Test
    void shouldStampDetectedAtWithWallClock_notWithPayloadTime() {
        // DTO nie niesie czasu, wiec serwis bierze ZonedDateTime.now(). Stad tolerancja.
        ZonedDateTime before = ZonedDateTime.now();
        h.service.handleDivergence(TestFixtures.divergenceAlert(Timeframe.H1, "LONG"));
        ZonedDateTime after = ZonedDateTime.now();

        assertThat(h.divergenceRepo.findAll().get(0).getDetectedAt())
                .isAfterOrEqualTo(before.minusSeconds(1))
                .isBeforeOrEqualTo(after.plusSeconds(1));
    }

    @Test
    void shouldEmitDivergenceDetectedEventCarryingTheSavedSignal() {
        h.service.handleDivergence(TestFixtures.divergenceAlert(Timeframe.H1, "LONG"));

        DivergenceDetectedEvent event = h.scenario.firstOfType(DivergenceDetectedEvent.class);
        assertThat(event.signal().getId()).isEqualTo(h.divergenceRepo.findAll().get(0).getId());
        // Istotne dla scenariuszy liczacych dywergencje w oknie: sygnal jest w repozytorium
        // ZANIM scenariusz go zobaczy, wiec liczy sie razem z historia.
        assertThat(h.divergenceRepo.findAll()).hasSize(1);
    }

    @Test
    void shouldDispatchAlerts_andRejectUnknownTimeframe() {
        AlertToSend alert = TestFixtures.alert(WebhookChainHarness.SCENARIO_NAME);
        WebhookChainHarness loud = WebhookChainHarness.returningAlerts(alert);

        loud.service.handleDivergence(TestFixtures.divergenceAlert(Timeframe.H4, "LONG"));
        assertThat(loud.publisher.allAlerts()).containsExactly(alert);

        DivergenceAlertDto broken = new DivergenceAlertDto(BTC.code(), "W2", "DIVERGENCE", "LONG");
        assertThatThrownBy(() -> h.service.handleDivergence(broken))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(h.divergenceRepo.findAll()).isEmpty();
    }
}
