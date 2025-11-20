package pl.tradeengine.application.dto;

import java.math.BigDecimal;

public record PriceUpdateDto(
        String symbol,
        String timeframe,
        String signalType,
        BigDecimal currentHigh,
        BigDecimal currentLow,
        BigDecimal close
) {}
