package pl.tradeengine.domain.scenario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.backtest.repository.InMemoryDivergenceRepository;
import pl.tradeengine.backtest.repository.InMemoryFvgRepository;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.event.FvgCreatedEvent;
import pl.tradeengine.domain.event.FvgTouchedEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.tradeengine.TestFixtures.BTC;
import static pl.tradeengine.TestFixtures.FIXED_NOW;
import static pl.tradeengine.domain.model.FvgStatus.CREATED;
import static pl.tradeengine.domain.model.FvgStatus.TOUCHED;

class FvgLtfConfirmationScenarioTest {

    private InMemoryFvgRepository fvgRepo;
    private InMemoryDivergenceRepository divergenceRepo;
    private FvgLtfConfirmationScenario scenario;

    @BeforeEach
    void setUp() {
        fvgRepo = new InMemoryFvgRepository();
        divergenceRepo = new InMemoryDivergenceRepository();
        scenario = new FvgLtfConfirmationScenario(fvgRepo, divergenceRepo);
    }

    // ─────────────────────────────────────────────────────────────
    // FvgTouchedEvent branch
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnEmpty_whenHtfFvgTouchedEventReceived() {
        FvgZone htfFvg = TestFixtures.fvgZone(Timeframe.H4, Direction.LONG, TOUCHED);

        List<AlertToSend> alerts = scenario.onEvent(new FvgTouchedEvent(htfFvg, FIXED_NOW));

        assertThat(alerts).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenFvgTouchedOnUnsupportedTimeframe() {
        FvgZone d1Fvg = TestFixtures.fvgZone(Timeframe.D1, Direction.LONG, TOUCHED);

        List<AlertToSend> alerts = scenario.onEvent(new FvgTouchedEvent(d1Fvg, FIXED_NOW));

        assertThat(alerts).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenUnknownEventType() {
        DomainEvent unknown = new DomainEvent() {};

        List<AlertToSend> alerts = scenario.onEvent(unknown);

        assertThat(alerts).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────
    // Divergence branch — happy paths (single-div sufficiency)
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldEmitAlert_whenSingleDivergenceOnH1AndHtfFvgArmed_longSetup() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(2));

        DivergenceSignal signal = TestFixtures.divergence(Timeframe.H1, Direction.LONG, FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).hasSize(1);
        AlertToSend alert = alerts.get(0);
        assertThat(alert.getSymbol()).isEqualTo(BTC);
        assertThat(alert.getDirection()).isEqualTo(Direction.LONG);
        assertThat(alert.getScenarioName()).isEqualTo("FVG_LTF_CONFIRMATION");
        assertThat(alert.getTimeframe()).isEqualTo(Timeframe.H1);
        assertThat(alert.getDescription()).contains("DIV H1");
    }

    @Test
    void shouldEmitAlert_whenSingleDivergenceOnM15AndHtfFvgArmed_shortSetup() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.SHORT, FIXED_NOW.minusHours(2));

        DivergenceSignal signal = TestFixtures.divergence(Timeframe.M15, Direction.SHORT, FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getDirection()).isEqualTo(Direction.SHORT);
        assertThat(alerts.get(0).getTimeframe()).isEqualTo(Timeframe.M15);
    }

    @Test
    void shouldEmitAlert_whenSingleDivergenceOnM5AndHtfFvgArmed() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(2));

        DivergenceSignal signal = TestFixtures.divergence(Timeframe.M5, Direction.LONG, FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getTimeframe()).isEqualTo(Timeframe.M5);
    }

    @Test
    void shouldEmitAlert_whenSingleDivergenceOnH4AndHtfFvgArmed() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(2));

        DivergenceSignal signal = TestFixtures.divergence(Timeframe.H4, Direction.LONG, FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getTimeframe()).isEqualTo(Timeframe.H4);
    }

    // ─────────────────────────────────────────────────────────────
    // Divergence branch — negative paths
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnEmpty_whenDivergenceOnUnsupportedTimeframe() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(2));

        DivergenceSignal signal = TestFixtures.divergence(Timeframe.D1, Direction.LONG, FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenNoArmedHtfFvgExists() {
        DivergenceSignal signal = TestFixtures.divergence(Timeframe.H1, Direction.LONG, FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenHtfFvgDirectionDoesNotMatchDivergenceDirection() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(2));

        DivergenceSignal signal = TestFixtures.divergence(Timeframe.H1, Direction.SHORT, FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────
    // Divergence count thresholds (M1 ≥ 4, M3 ≥ 2)
    // Seed the fake repo so findAllByDirectionSince returns the expected count.
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnEmpty_whenM1DivergenceCountBelowThreshold() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(2));
        seedDivergences(3, Timeframe.M1, Direction.LONG, FIXED_NOW);

        DivergenceSignal signal = TestFixtures.divergence(Timeframe.M1, Direction.LONG, FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).isEmpty();
    }

    @Test
    void shouldEmitAlert_whenM1DivergenceCountMeetsThreshold() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(2));
        seedDivergences(4, Timeframe.M1, Direction.LONG, FIXED_NOW);

        DivergenceSignal signal = TestFixtures.divergence(Timeframe.M1, Direction.LONG, FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getTimeframe()).isEqualTo(Timeframe.M1);
        assertThat(alerts.get(0).getDescription()).contains("DIV M1 x4");
    }

    @Test
    void shouldReturnEmpty_whenM3DivergenceCountBelowThreshold() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(2));
        seedDivergences(1, Timeframe.M3, Direction.LONG, FIXED_NOW);

        DivergenceSignal signal = TestFixtures.divergence(Timeframe.M3, Direction.LONG, FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).isEmpty();
    }

    @Test
    void shouldEmitAlert_whenM3DivergenceCountMeetsThreshold() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(2));
        seedDivergences(2, Timeframe.M3, Direction.LONG, FIXED_NOW);

        DivergenceSignal signal = TestFixtures.divergence(Timeframe.M3, Direction.LONG, FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getDescription()).contains("DIV M3 x2");
    }

    // ─────────────────────────────────────────────────────────────
    // LTF FVG branch
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldEmitAlert_whenLtfFvgCreatedOnM5InSameDirectionAsArmedHtfFvg() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(2));

        FvgZone ltfFvg = TestFixtures.fvgZone(Timeframe.M5, Direction.LONG, CREATED);

        List<AlertToSend> alerts = scenario.onEvent(new FvgCreatedEvent(ltfFvg));

        assertThat(alerts).hasSize(1);
        AlertToSend alert = alerts.get(0);
        assertThat(alert.getDirection()).isEqualTo(Direction.LONG);
        assertThat(alert.getTimeframe()).isEqualTo(Timeframe.M5);
        assertThat(alert.getScenarioName()).isEqualTo("FVG_LTF_CONFIRMATION");
        assertThat(alert.getDescription()).contains("LTF_FVG M5");
    }

    @Test
    void shouldReturnEmpty_whenLtfFvgCreatedOnUnsupportedTimeframe() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(2));

        FvgZone ltfFvg = TestFixtures.fvgZone(Timeframe.H4, Direction.LONG, CREATED);

        List<AlertToSend> alerts = scenario.onEvent(new FvgCreatedEvent(ltfFvg));

        assertThat(alerts).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenLtfFvgCreatedButHtfFvgDirectionMismatch() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(2));

        FvgZone ltfFvg = TestFixtures.fvgZone(Timeframe.M5, Direction.SHORT, CREATED);

        List<AlertToSend> alerts = scenario.onEvent(new FvgCreatedEvent(ltfFvg));

        assertThat(alerts).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenLtfFvgCreatedButNoArmedHtfFvgExists() {
        // No HTF FVG saved at all.
        FvgZone ltfFvg = TestFixtures.fvgZone(Timeframe.M5, Direction.LONG, CREATED);

        List<AlertToSend> alerts = scenario.onEvent(new FvgCreatedEvent(ltfFvg));

        assertThat(alerts).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────
    // HTF preference + FILLED-status acceptance
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldPreferH4OverH1_whenBothHtfFvgsAreArmed() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H1, Direction.LONG, FIXED_NOW.minusHours(1));
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(2));

        FvgZone ltfFvg = TestFixtures.fvgZone(Timeframe.M5, Direction.LONG, CREATED);

        List<AlertToSend> alerts = scenario.onEvent(new FvgCreatedEvent(ltfFvg));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getDescription()).contains("CONTEXT: FVG H4");
    }

    @Test
    void shouldEmitAlert_whenHtfFvgIsFilled() {
        // Code passes [TOUCHED, FILLED] to the repo, so FILLED zones still count as armed context.
        TestFixtures.saveFilledFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));

        DivergenceSignal signal = TestFixtures.divergence(Timeframe.H1, Direction.LONG, FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getDescription()).contains("FILLED");
    }

    // ─────────────────────────────────────────────────────────────
    // Luki domkniete pozniej: H1 jako kontekst, okno 40 swiec, pozostale LTF
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnEmpty_whenH1FvgTouchedEventReceived() {
        // Galaz FvgTouchedEvent byla sprawdzona tylko dla H4. H1 tez jest kontekstem HTF i tez
        // ma tylko sie zalogowac — uzbrojenie jest niejawne, siedzi w statusie w repozytorium.
        FvgZone htfFvg = TestFixtures.fvgZone(Timeframe.H1, Direction.SHORT, TOUCHED);

        assertThat(scenario.onEvent(new FvgTouchedEvent(htfFvg, FIXED_NOW))).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenDivergencesAreOldEnoughToFallOutOfTheLookbackWindow() {
        // Dotad sprawdzane bylo tylko "za malo sztuk". Tutaj sztuk jest dosc (4 na M1), ale
        // leza poza oknem 40 swiec, czyli poza 40 minutami — wiec zapytanie ich nie widzi.
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));
        for (int i = 0; i < 3; i++) {
            divergenceRepo.save(TestFixtures.divergence(Timeframe.M1, Direction.LONG, FIXED_NOW.minusHours(2).minusMinutes(i)));
        }
        DivergenceSignal current = divergenceRepo.save(TestFixtures.divergence(Timeframe.M1, Direction.LONG, FIXED_NOW));

        assertThat(scenario.onEvent(new DivergenceDetectedEvent(current))).isEmpty();
    }

    @Test
    void shouldCountOnlyDivergencesInsideTheWindow_whenSomeAreTooOld() {
        // Trzy w oknie + jedna poza = 3 < wymagane 4 na M1.
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));
        divergenceRepo.save(TestFixtures.divergence(Timeframe.M1, Direction.LONG, FIXED_NOW.minusMinutes(45)));
        seedDivergences(2, Timeframe.M1, Direction.LONG, FIXED_NOW);
        DivergenceSignal current = divergenceRepo.save(TestFixtures.divergence(Timeframe.M1, Direction.LONG, FIXED_NOW));

        assertThat(scenario.onEvent(new DivergenceDetectedEvent(current))).isEmpty();
    }

    @Test
    void shouldEmitAlert_whenLtfFvgIsCreatedOnM15() {
        // LTF_FVG_TIMEFRAMES to [M5, M15, H1]; dotad sprawdzone bylo tylko M5 i odrzucenie H4.
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));
        FvgZone ltfFvg = TestFixtures.fvgZone(Timeframe.M15, Direction.LONG, CREATED);

        List<AlertToSend> alerts = scenario.onEvent(new FvgCreatedEvent(ltfFvg));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getTimeframe()).isEqualTo(Timeframe.M15);
        assertThat(alerts.get(0).getDescription()).contains("LTF_FVG M15");
    }

    @Test
    void shouldEmitAlert_whenLtfFvgIsCreatedOnH1_whichIsBothContextAndTrigger() {
        // H1 jest jednoczesnie w HTF_CONTEXT_TIMEFRAMES i w LTF_FVG_TIMEFRAMES. Tutaj kontekstem
        // jest H4, a triggerem H1 — i to przechodzi.
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(2));
        FvgZone ltfFvg = TestFixtures.fvgZone(Timeframe.H1, Direction.LONG, CREATED);

        assertThat(scenario.onEvent(new FvgCreatedEvent(ltfFvg))).hasSize(1);
    }

    @Test
    void shouldRejectLtfFvg_onTimeframesOutsideTheTriggerSet() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));

        for (Timeframe tf : new Timeframe[]{Timeframe.M1, Timeframe.M3, Timeframe.D1}) {
            assertThat(scenario.onEvent(new FvgCreatedEvent(TestFixtures.fvgZone(tf, Direction.LONG, CREATED))))
                    .as("LTF FVG na %s", tf)
                    .isEmpty();
        }
    }

    @Test
    void shouldUseH1Context_whenItIsTheOnlyArmedHtfFvg() {
        // Dotad sprawdzona byla tylko preferencja H4 nad H1. Sam H1 tez jest kontekstem.
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H1, Direction.SHORT, FIXED_NOW.minusHours(1));
        DivergenceSignal signal = divergenceRepo.save(TestFixtures.divergence(Timeframe.M15, Direction.SHORT, FIXED_NOW));

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getDescription()).contains("CONTEXT: FVG H1");
    }

    @Test
    void shouldRejectCreatedAndConsumedHtfZones_asContext() {
        // findArmedHtfFvg odpytuje o statusy [TOUCHED, FILLED]. Strefa swiezo utworzona (CREATED)
        // nie jest jeszcze kontekstem, a CONSUMED juz nim nie jest.
        fvgRepo.save(TestFixtures.fvgZone(Timeframe.H4, Direction.LONG, CREATED));
        fvgRepo.save(TestFixtures.fvgZone(Timeframe.H1, Direction.LONG, pl.tradeengine.domain.model.FvgStatus.CONSUMED));
        DivergenceSignal signal = divergenceRepo.save(TestFixtures.divergence(Timeframe.M15, Direction.LONG, FIXED_NOW));

        assertThat(scenario.onEvent(new DivergenceDetectedEvent(signal))).isEmpty();
    }

    @Test
    void shouldEmitExactlyOneAlert_whenTwoArmedZonesShareTheSameTimeframe() {
        // Przy remisie na timeframe'ie sortowanie nie rozstrzyga, a findFirst bierze cokolwiek.
        // Utrwalona jest liczba alertow — jeden trigger to jeden alert, nie dwa.
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(3));
        DivergenceSignal signal = divergenceRepo.save(TestFixtures.divergence(Timeframe.M15, Direction.LONG, FIXED_NOW));

        assertThat(scenario.onEvent(new DivergenceDetectedEvent(signal))).hasSize(1);
    }

    @Test
    void shouldIgnoreZonesOfAnotherSymbol() {
        // Caly dotychczasowy zestaw chodzi na jednym symbolu, wiec filtr po symbolu nie byl
        // nigdy sprawdzony — a to on decyduje, czy alert na ETH nie wezmie kontekstu z BTC.
        pl.tradeengine.domain.model.Symbol eth = new pl.tradeengine.domain.model.Symbol("ETHUSDT");
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));
        DivergenceSignal ethSignal = divergenceRepo.save(
                new DivergenceSignal(null, eth, Timeframe.M15, Direction.LONG, 1.0, FIXED_NOW));

        assertThat(scenario.onEvent(new DivergenceDetectedEvent(ethSignal))).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private void seedDivergences(int count, Timeframe tf, Direction direction, ZonedDateTime currentSignalAt) {
        // Place all seeded divs 1 minute before the current signal — well inside the 40-candle window
        // for any supported TF (M1 = 40 min, all others are longer).
        for (int i = 0; i < count; i++) {
            divergenceRepo.save(TestFixtures.divergence(tf, direction, currentSignalAt.minusMinutes(1 + i)));
        }
    }
}
