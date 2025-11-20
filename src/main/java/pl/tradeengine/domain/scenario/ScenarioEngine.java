package pl.tradeengine.domain.scenario;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.model.AlertToSend;

import java.util.List;

@Slf4j
public class ScenarioEngine {

    private final List<Scenario> scenarios;

    public ScenarioEngine(List<Scenario> scenarios) {
        this.scenarios = List.copyOf(scenarios);
        log.info("Active scenarios (strategies):");
        this.scenarios.forEach(s -> log.info(" - {}", s.name()));
    }

    public List<AlertToSend> onEvent(DomainEvent event) {
        return scenarios.stream()
                .map(s -> s.onEvent(event))
                .flatMap(List::stream)
                .toList();
    }
}
