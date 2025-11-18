package pl.tradeengine.domain.scenario;

import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.model.AlertToSend;

import java.util.List;

public interface Scenario {
    List<AlertToSend> onEvent(DomainEvent event);
    Long id();
    String name();
}
