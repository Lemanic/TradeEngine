package pl.tradeengine.application.dto;

public record PriceUpdateDto(
        String symbol,
        String timeframe,
        String signalType,
        double currentHigh,
        double currentLow
) {}
