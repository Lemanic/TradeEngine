package pl.tradeengine.application.dto;

// application/dto/PriceUpdateDto.java
public record PriceUpdateDto(
        String symbol,
        String timeframe,    // "M5"
        String signalType,   // "PRICE_UPDATE"
        double currentHigh,
        double currentLow
) {}
