package pl.tradeengine.application.dto;

import java.math.BigDecimal;

public record PriceUpdateDto(
        String symbol,
        String timeframe,
        String signalType,
//        double currentHigh,
//        double currentLow,
//        Double close
        BigDecimal currentHigh, // <-- ZMIEŃ TYP
        BigDecimal currentLow,  // <-- ZMIEŃ TYP
        BigDecimal close
) {}
