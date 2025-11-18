package pl.tradeengine.domain.scenario;

import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.model.AlertToSend;

import java.util.List;

public class ScenarioEngine {

    private final List<Scenario> scenarios;

    public ScenarioEngine(List<Scenario> scenarios) {
        this.scenarios = List.copyOf(scenarios);
    }

    public List<AlertToSend> onEvent(DomainEvent event) {
        return scenarios.stream()
                .map(s -> s.onEvent(event))
                .flatMap(List::stream)
                .toList();
    }
}
