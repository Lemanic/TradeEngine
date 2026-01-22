package pl.tradeengine.backtest.engine;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.backtest.registry.BacktestScenarioRegistry;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.model.AlertToSend;

import java.util.List;

@Slf4j
public class BacktestScenarioEngine {

    private final BacktestScenarioRegistry scenarioRegistry;

    public BacktestScenarioEngine(BacktestScenarioRegistry scenarioRegistry) {
        this.scenarioRegistry = scenarioRegistry;
    }

    public List<AlertToSend> onEvent(DomainEvent event) {
        // Prosta implementacja: wszystkie scenariusze dostają każdy event
        return scenarioRegistry.getAllEnabledScenarios().stream()
                .flatMap(scenario -> scenario.onEvent(event).stream())
                .toList();
    }
}
