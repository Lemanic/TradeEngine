package pl.tradeengine.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tradeengine.RecordingBiasRepositoryFake;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.application.dto.MomentumAlertDto;
import pl.tradeengine.domain.event.SwingPointDetectedEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.BiasStatus;
import pl.tradeengine.domain.model.StoredSwingPoint;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.tradeengine.TestFixtures.BTC;

/**
 * Jedyna galez tej metody to BIAS_TIMEFRAMES = {D1, W1}: wyzsze timeframe'y aktualizuja bias,
 * nizsze rejestruja swing point. CLAUDE.md wymaga, zeby ten podzial byl zgodny z
 * BacktestRunner.handleWaveTrendCross — te testy sa miejscem, gdzie rozjazd stanie sie widoczny.
 */
class WebhookProcessingServiceHandleMomentumAlertTest {

    private WebhookChainHarness h;

    @BeforeEach
    void setUp() {
        h = WebhookChainHarness.silent();
    }

    // ─────────────────────────────────────────────────────────────
    // D1 / W1 -> bias
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldUpdateBiasWithTimeframeInReason_whenAlertIsOnD1() {
        h.service.handleMomentumAlert(TestFixtures.momentumAlert(Timeframe.D1, "BULLISH"));

        assertThat(h.biasRepo.updates()).hasSize(1);
        RecordingBiasRepositoryFake.BiasUpdate update = h.biasRepo.updates().get(0);
        assertThat(update.symbol()).isEqualTo(BTC);
        assertThat(update.timeframe()).isEqualTo(Timeframe.D1);
        assertThat(update.status()).isEqualTo(BiasStatus.BULLISH);
        // Reason to jedyny slad pochodzenia biasu w bazie (kolumna last_reason).
        assertThat(update.reason()).isEqualTo("MOMENTUM_WAVE_D1");
    }

    @Test
    void shouldUpdateBias_whenAlertIsOnW1() {
        h.service.handleMomentumAlert(TestFixtures.momentumAlert(Timeframe.W1, "BEARISH"));

        assertThat(h.biasRepo.getBias(BTC, Timeframe.W1)).isEqualTo(BiasStatus.BEARISH);
        assertThat(h.biasRepo.updates().get(0).reason()).isEqualTo("MOMENTUM_WAVE_W1");
    }

    @Test
    void shouldEmitNoEventAtAll_whenUpdatingBias() {
        // Sciezka biasu konczy sie na repozytorium: zaden scenariusz nie reaguje na zmiane biasu
        // bezposrednio, tylko odczytuje go pozniej. Latwo to zgubic przy refaktorze.
        h.service.handleMomentumAlert(TestFixtures.momentumAlert(Timeframe.D1, "BULLISH"));

        assertThat(h.scenario.seen()).isEmpty();
        assertThat(h.publisher.published()).isEmpty();
        assertThat(h.swingRepo.saved()).isEmpty();
    }

    @Test
    void shouldAcceptLowercaseDirection_becauseTheRawValueIsUppercasedFirst() {
        h.service.handleMomentumAlert(TestFixtures.momentumAlert(Timeframe.D1, "bullish"));

        assertThat(h.biasRepo.updates().get(0).status()).isEqualTo(BiasStatus.BULLISH);
    }

    @Test
    void shouldExitSilently_whenDirectionIsNotAValidBiasStatus() {
        // Jedyny try/catch w tej metodzie: nieznana wartosc to log.warn i return, bez wyjatku.
        // Z punktu widzenia TradingView webhook konczy sie 202, a bias zostaje stary — czyli
        // literowka w alercie Pine Script jest calkowicie niewidoczna.
        h.service.handleMomentumAlert(TestFixtures.momentumAlert(Timeframe.D1, "LONG"));

        assertThat(h.biasRepo.updates()).isEmpty();
        assertThat(h.biasRepo.getBias(BTC, Timeframe.D1)).isEqualTo(BiasStatus.NEUTRAL);
        assertThat(h.scenario.seen()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────
    // Pozostale timeframe'y -> swing point
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldRegisterSwingLow_whenBullishAlertIsBelowBiasTimeframes() {
        h.service.handleMomentumAlert(TestFixtures.momentumAlert(Timeframe.H1, "BULLISH"));

        assertThat(h.biasRepo.updates()).isEmpty();
        assertThat(h.swingRepo.saved()).hasSize(1);
        StoredSwingPoint saved = h.swingRepo.saved().get(0);
        assertThat(saved.symbol()).isEqualTo(BTC);
        assertThat(saved.timeframe()).isEqualTo(Timeframe.H1);
        // BULLISH momentum = wlasnie uformowal sie dolek.
        assertThat(saved.type()).isEqualTo("SWING_LOW");
        // Cena to zaslepka: MomentumAlertDto nie niesie zadnej ceny.
        assertThat(saved.price()).isEqualByComparingTo("0");
    }

    @Test
    void shouldRegisterSwingHigh_whenDirectionIsAnythingOtherThanBullish() {
        // Warunek w produkcji to "BULLISH".equals(raw) z else — wiec BEARISH, NEUTRAL i kazda
        // inna wartosc daja SWING_HIGH. Dla NEUTRAL jest to zapis szczytu, ktorego nie bylo.
        for (String direction : new String[]{"BEARISH", "NEUTRAL", "COSKOLWIEK"}) {
            WebhookChainHarness fresh = WebhookChainHarness.silent();

            fresh.service.handleMomentumAlert(TestFixtures.momentumAlert(Timeframe.M15, direction));

            assertThat(fresh.swingRepo.saved().get(0).type())
                    .as("kierunek %s", direction)
                    .isEqualTo("SWING_HIGH");
        }
    }

    @Test
    void shouldEmitSwingPointEventMatchingWhatWasStored() {
        ZonedDateTime before = ZonedDateTime.now();
        h.service.handleMomentumAlert(TestFixtures.momentumAlert(Timeframe.H4, "BULLISH"));
        ZonedDateTime after = ZonedDateTime.now();

        SwingPointDetectedEvent event = h.scenario.firstOfType(SwingPointDetectedEvent.class);
        StoredSwingPoint stored = h.swingRepo.saved().get(0);
        assertThat(event.symbol()).isEqualTo(stored.symbol());
        assertThat(event.timeframe()).isEqualTo(stored.timeframe());
        assertThat(event.type()).isEqualTo(stored.type());
        assertThat(event.price()).isEqualByComparingTo(stored.price());
        assertThat(event.detectedAt()).isEqualTo(stored.detectedAt())
                .isAfterOrEqualTo(before.minusSeconds(1))
                .isBeforeOrEqualTo(after.plusSeconds(1));
    }

    @Test
    void shouldDispatchAlerts_whenScenarioReactsToTheSwingPoint() {
        AlertToSend alert = TestFixtures.alert(WebhookChainHarness.SCENARIO_NAME);
        WebhookChainHarness loud = WebhookChainHarness.returningAlerts(alert);

        loud.service.handleMomentumAlert(TestFixtures.momentumAlert(Timeframe.H1, "BULLISH"));

        assertThat(loud.publisher.allAlerts()).containsExactly(alert);
    }

    @Test
    void shouldThrow_whenTimeframeIsUnknown() {
        MomentumAlertDto broken = new MomentumAlertDto(BTC.code(), "M2", "MOMENTUM_WAVE", "BULLISH");

        assertThatThrownBy(() -> h.service.handleMomentumAlert(broken))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(h.swingRepo.saved()).isEmpty();
        assertThat(h.biasRepo.updates()).isEmpty();
    }
}
