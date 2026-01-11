package pl.tradeengine.domain.event;

import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record SwingPointDetectedEvent(
        Symbol symbol,
        Timeframe timeframe,
        String type,
        BigDecimal price,
        ZonedDateTime detectedAt
) implements DomainEvent {}
