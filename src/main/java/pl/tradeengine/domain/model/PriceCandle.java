package pl.tradeengine.domain.model;

import java.time.ZonedDateTime;
import java.math.BigDecimal;

public record PriceCandle(
        Symbol symbol,
        Timeframe timeframe,
        ZonedDateTime openTime,
        ZonedDateTime closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close
) {}
