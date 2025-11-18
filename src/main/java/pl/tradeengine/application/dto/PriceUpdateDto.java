package pl.tradeengine.application.dto;

public record PriceUpdateDto(
        String symbol,
        String timeframe,
        String signalType,   // "PRICE_UPDATE"
        double currentHigh,
        double currentLow
) {}
