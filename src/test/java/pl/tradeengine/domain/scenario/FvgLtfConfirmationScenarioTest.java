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
