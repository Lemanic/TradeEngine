package pl.tradeengine.domain.event;

import pl.tradeengine.domain.model.FvgZone;

import java.time.ZonedDateTime;

public record FvgTouchedEvent(
        FvgZone fvgZone,
        ZonedDateTime touchedAt
) implements DomainEvent {
}