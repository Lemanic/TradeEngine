package pl.tradeengine.application.dto;

import java.math.BigDecimal;

public record FvgAlertDto(
        String symbol,
        String timeframe,
        String signalType,
        String direction,
        BigDecimal fvgLow,
        BigDecimal fvgHigh,
        String fvgStatus
) {}
