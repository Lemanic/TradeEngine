package pl.tradeengine.domain.model;

import java.time.ZonedDateTime;

public record MarketBias(
        Symbol symbol,
        Timeframe timeframe,
        BiasStatus status,
        ZonedDateTime updatedAt
) {}
