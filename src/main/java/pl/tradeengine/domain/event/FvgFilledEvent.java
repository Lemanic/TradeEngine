package pl.tradeengine.domain.event;

import pl.tradeengine.domain.model.FvgZone;

import java.time.ZonedDateTime;

public record FvgFilledEvent(
        FvgZone fvgZone,
        ZonedDateTime filledAt
) implements DomainEvent {
}