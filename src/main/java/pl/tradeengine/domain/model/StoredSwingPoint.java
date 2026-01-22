package pl.tradeengine.domain.model;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record StoredSwingPoint(
        Symbol symbol,
        Timeframe timeframe,
        String type, // SWING_HIGH / SWING_LOW
        BigDecimal price,
        ZonedDateTime detectedAt
) {}