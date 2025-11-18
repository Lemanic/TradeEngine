package pl.tradeengine.domain.model;

import java.time.ZonedDateTime;

public record PriceCandle(
        Symbol symbol,
        Timeframe timeframe,
        ZonedDateTime openTime,
        ZonedDateTime closeTime,
        double open,
        double high,
        double low,
        double close
) {}