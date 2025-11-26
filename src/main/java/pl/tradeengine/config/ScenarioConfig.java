package pl.tradeengine.config;

import org.springframework.context.annotation.Configuration;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Timeframe;
import java.util.List;

@Configuration
public class ScenarioConfig {

    private final ScenarioRegistry scenarioRegistry;

    public ScenarioConfig(ScenarioRegistry scenarioRegistry) {
        this.scenarioRegistry = scenarioRegistry;
    }

    public List<AlertToSend> handle(DomainEvent event) {
        Timeframe tf = ((DivergenceDetectedEvent) event).signal().getTimeframe();
        return scenarioRegistry.getScenariosFor(tf).stream()
                .flatMap(s -> s.onEvent(event).stream())
                .toList();
    }
}

