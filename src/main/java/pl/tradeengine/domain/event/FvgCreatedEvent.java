package pl.tradeengine.domain.event;

import pl.tradeengine.domain.model.FvgZone;

public record FvgCreatedEvent(
        FvgZone fvgZone
) implements DomainEvent {
}
