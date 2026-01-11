package pl.tradeengine.application.dto;

import java.math.BigDecimal;

public record SwingPointDto(
        String symbol,
        String timeframe,
        String type,
        BigDecimal price
) {}
