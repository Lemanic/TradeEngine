package pl.tradeengine.domain.scenario;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.tradeengine.config.ScenarioRegistry;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Timeframe;

import java.util.List;

@Slf4j
@Component
public class ScenarioEngine {

    private final ScenarioRegistry scenarioRegistry;

    public ScenarioEngine(ScenarioRegistry scenarioRegistry) {
        this.scenarioRegistry = scenarioRegistry;
    }

    public List<AlertToSend> onEvent(DomainEvent event) {
        if (!(event instanceof DivergenceDetectedEvent divergenceEvent)) {
            return List.of();
        }

        Timeframe timeframe = divergenceEvent.signal().getTimeframe();
        var scenariosForTimeframe = scenarioRegistry.getScenariosFor(timeframe);

        return scenariosForTimeframe.stream()
                .flatMap(s -> s.onEvent(event).stream())
                .toList();
    }
}