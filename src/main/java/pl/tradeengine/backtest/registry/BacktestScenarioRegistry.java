package pl.tradeengine.backtest.registry;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.scenario.Scenario;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class BacktestScenarioRegistry {

    private final List<Scenario> scenarios = new ArrayList<>();

    public void register(Scenario scenario) {
        scenarios.add(scenario);
        log.info("Registered backtest scenario: {}", scenario.name());
    }

    public List<Scenario> getScenariosFor(Timeframe timeframe) {
        return new ArrayList<>(scenarios);
    }

    public List<Scenario> getAllEnabledScenarios() {
        return new ArrayList<>(scenarios);
    }
}
