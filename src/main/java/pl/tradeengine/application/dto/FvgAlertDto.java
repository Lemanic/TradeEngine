package pl.tradeengine.application.dto;

public record FvgAlertDto(
        String symbol,
        String timeframe,
        String signalType,
        String direction,
        double fvgLow,
        double fvgHigh,
        String fvgStatus
) {}
