package pl.tradeengine.application.dto;

public record FvgAlertDto(
        String symbol,
        String timeframe,
        String signalType,  // "FVG_CREATED", iFVG_CREATED
        String direction,
        double fvgLow,
        double fvgHigh,
        String fvgStatus
) {}
