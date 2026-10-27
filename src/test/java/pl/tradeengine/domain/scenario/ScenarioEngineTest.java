package pl.tradeengine.domain.scenario;

import org.junit.jupiter.api.Test;
import pl.tradeengine.RecordingScenario;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.config.ScenarioRegistry;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.event.FvgCreatedEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.Timeframe;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.tradeengine.TestFixtures.FIXED_NOW;

/**
 * Rozdzielnia zdarzen. Cala jej tresc to jedna galez: DivergenceDetectedEvent trafia tylko do
 * scenariuszy wlaczonych na timeframe sygnalu, a kazde inne zdarzenie do wszystkich wlaczonych,
 * bez wzgledu na timeframe. Ta asymetria nie wynika z zadnego pliku osobno.
 */
class ScenarioEngineTest {

    private static ScenarioEngine engineOver(List<pl.tradeengine.domain.scenario.Scenario> scenarios,
                                             pl.tradeengine.config.StrategyProperties props) {
        return new ScenarioEngine(new ScenarioRegistry(scenarios, props));
    }

    private static DivergenceDetectedEvent divergenceOn(Timeframe tf) {
        return new DivergenceDetectedEvent(TestFixtures.divergence(tf, Direction.LONG, FIXED_NOW));
    }

    private static final DomainEvent NON_DIVERGENCE =
            new FvgCreatedEvent(TestFixtures.fvgZone(Timeframe.H4, Direction.LONG, FvgStatus.CREATED));

    @Test
    void shouldRouteDivergence_onlyToScenariosEnabledOnItsTimeframe() {
        RecordingScenario onM5 = RecordingScenario.silent("ON_M5");
        RecordingScenario onH4 = RecordingScenario.silent("ON_H4");
        ScenarioEngine engine = engineOver(List.of(onM5, onH4), TestFixtures.strategies(
                TestFixtures.strategy("ON_M5", true, Timeframe.M5),
                TestFixtures.strategy("ON_H4", true, Timeframe.H4)));

        engine.onEvent(divergenceOn(Timeframe.M5));

        assertThat(onM5.seen()).hasSize(1);
        assertThat(onH4.seen()).isEmpty();
    }

    @Test
    void shouldRouteEveryOtherEvent_toAllEnabledScenariosRegardlessOfTimeframe() {
        // Zdarzenie dotyczy H4, a scenariusz jest wlaczony tylko na M5 — i mimo to je dostaje.
        RecordingScenario onM5 = RecordingScenario.silent("ON_M5");
        ScenarioEngine engine = engineOver(List.of(onM5), TestFixtures.strategies(
                TestFixtures.strategy("ON_M5", true, Timeframe.M5)));

        engine.onEvent(NON_DIVERGENCE);

        assertThat(onM5.seen()).containsExactly(NON_DIVERGENCE);
    }

    @Test
    void shouldConcatenateAlerts_fromAllMatchingScenariosInConfigOrder() {
        AlertToSend fromFirst = TestFixtures.alert("FIRST");
        AlertToSend fromSecond = TestFixtures.alert("SECOND");
        ScenarioEngine engine = engineOver(
                List.of(RecordingScenario.returning("FIRST", fromFirst),
                        RecordingScenario.returning("SECOND", fromSecond)),
                TestFixtures.strategies(
                        TestFixtures.strategy("FIRST", true, Timeframe.H1),
                        TestFixtures.strategy("SECOND", true, Timeframe.H1)));

        assertThat(engine.onEvent(NON_DIVERGENCE)).containsExactly(fromFirst, fromSecond);
        // Kolejnosc idzie z listy w YAML, nie z kolejnosci beanow — ScenarioRegistry iteruje po
        // strategyProperties, a nie po wstrzyknietej liscie scenariuszy.
        assertThat(engine.onEvent(divergenceOn(Timeframe.H1))).containsExactly(fromFirst, fromSecond);
    }

    @Test
    void shouldReturnNoAlerts_whenScenariosReturnNothing() {
        ScenarioEngine engine = engineOver(
                List.of(RecordingScenario.silent("QUIET")),
                TestFixtures.strategies(TestFixtures.strategy("QUIET", true, Timeframe.H1)));

        assertThat(engine.onEvent(NON_DIVERGENCE)).isEmpty();
    }

    @Test
    void shouldReturnNoAlerts_whenNoStrategyIsEnabled() {
        ScenarioEngine engine = engineOver(
                List.of(RecordingScenario.returning("OFF", TestFixtures.alert("OFF"))),
                TestFixtures.strategies(TestFixtures.strategy("OFF", false, Timeframe.H1)));

        assertThat(engine.onEvent(NON_DIVERGENCE)).isEmpty();
        assertThat(engine.onEvent(divergenceOn(Timeframe.H1))).isEmpty();
    }

    @Test
    void shouldReturnNoAlerts_whenRegistryHasNoScenariosAtAll() {
        ScenarioEngine engine = engineOver(List.of(), TestFixtures.strategies());

        assertThat(engine.onEvent(NON_DIVERGENCE)).isEmpty();
        assertThat(engine.onEvent(divergenceOn(Timeframe.H1))).isEmpty();
    }
}
