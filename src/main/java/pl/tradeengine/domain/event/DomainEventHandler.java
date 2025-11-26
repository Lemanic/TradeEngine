package pl.tradeengine.domain.event;

import org.springframework.stereotype.Component;
import pl.tradeengine.config.ScenarioRegistry;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Timeframe;

import java.util.List;

@Component
public class DomainEventHandler {

    private final ScenarioRegistry scenarioRegistry;

    public DomainEventHandler(ScenarioRegistry scenarioRegistry) {
        this.scenarioRegistry = scenarioRegistry;
    }

    public List<AlertToSend> handle(DomainEvent event) {
        Timeframe tf = extractTimeframe(event);
        return scenarioRegistry.getScenariosFor(tf).stream()
                .flatMap(s -> s.onEvent(event).stream())
                .toList();
    }

    private Timeframe extractTimeframe(DomainEvent event) {
        return ((pl.tradeengine.domain.event.DivergenceDetectedEvent) event)
                .signal()
                .getTimeframe();
    }
}