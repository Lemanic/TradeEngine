package pl.tradeengine.backtest.registry;

import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.scenario.Scenario;

import java.util.ArrayList;
import java.util.List;


public class InMemoryScenarioRegistry {

    private final List<Scenario> scenarios = new ArrayList<>();

    public void register(Scenario scenario) {
        scenarios.add(scenario);
    }

    public List<Scenario> getScenariosFor(Timeframe timeframe) {
        // W backteście zwracamy wszystkie scenariusze
        return new ArrayList<>(scenarios);
    }

    public List<Scenario> getAllEnabledScenarios() {
        return new ArrayList<>(scenarios);
    }
}
