package pl.tradeengine.domain.scenario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.backtest.repository.InMemoryBiasRepository;
import pl.tradeengine.backtest.repository.InMemoryFvgRepository;
import pl.tradeengine.backtest.repository.InMemorySwingPointRepository;
import pl.tradeengine.domain.event.SwingPointDetectedEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.BiasStatus;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.Timeframe;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.tradeengine.TestFixtures.BTC;
import static pl.tradeengine.TestFixtures.FIXED_NOW;

class GrinderStrategyScenarioTest {

    private InMemoryFvgRepository fvgRepo;
    private InMemoryBiasRepository biasRepo;
    private GrinderStrategyScenario scenario;

    @BeforeEach
    void setUp() {
        fvgRepo = new InMemoryFvgRepository();
        biasRepo = new InMemoryBiasRepository();
        InMemorySwingPointRepository swingRepo = new InMemorySwingPointRepository();

        scenario = new GrinderStrategyScenario(
                fvgRepo, biasRepo, swingRepo,
                "TEST_GRINDER",
                Timeframe.D1,
                List.of(Timeframe.H4, Timeframe.D1),
                Timeframe.H1
        );
    }

    @Test
    void shouldReturnNoAlerts_whenBiasContradictsSwingPointDirection() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BEARISH, "test");
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));

        SwingPointDetectedEvent bullishSwing = TestFixtures.bullishSwingOnH1(FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(bullishSwing);

        assertThat(alerts).isEmpty();
    }

    @Test
    void shouldGenerateAlert_whenBiasAndPoiAndSwingPointAreAligned_longSetup() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));

        SwingPointDetectedEvent bullishSwing = TestFixtures.bullishSwingOnH1(FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(bullishSwing);

        assertThat(alerts).hasSize(1);
        AlertToSend alert = alerts.get(0);
        assertThat(alert.getDirection()).isEqualTo(Direction.LONG);
        assertThat(alert.getSymbol()).isEqualTo(BTC);
        assertThat(alert.getScenarioName()).isEqualTo("TEST_GRINDER");
        assertThat(alert.getTimeframe()).isEqualTo(Timeframe.H1);
    }

    @Test
    void shouldGenerateAlert_whenBiasAndPoiAndSwingPointAreAligned_shortSetup() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BEARISH, "test");
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.SHORT, FIXED_NOW.minusHours(1));

        SwingPointDetectedEvent bearishSwing = TestFixtures.bearishSwingOnH1(FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(bearishSwing);

        assertThat(alerts).hasSize(1);
        AlertToSend alert = alerts.get(0);
        assertThat(alert.getDirection()).isEqualTo(Direction.SHORT);
        assertThat(alert.getSymbol()).isEqualTo(BTC);
        assertThat(alert.getScenarioName()).isEqualTo("TEST_GRINDER");
    }

    @Test
    void shouldReturnNoAlerts_whenSwingPointTimeframeDoesNotMatchTriggerTimeframe() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));

        SwingPointDetectedEvent swingOnD1 = TestFixtures.swingEvent(Timeframe.D1, "SWING_LOW", FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(swingOnD1);

        assertThat(alerts).isEmpty();
    }

    @Test
    void shouldReturnNoAlerts_whenNoPOIFvgExistsForSwingPoint() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");

        SwingPointDetectedEvent bullishSwing = TestFixtures.bullishSwingOnH1(FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(bullishSwing);

        assertThat(alerts).isEmpty();
    }
}
