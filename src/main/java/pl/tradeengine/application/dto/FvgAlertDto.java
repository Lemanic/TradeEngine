package pl.tradeengine.application.dto;

public record FvgAlertDto(
        String symbol,
        String timeframe,
        String signalType,  // "FVG_CREATED"
        String direction,   // "LONG" / "SHORT"
        double fvgLow,
        double fvgHigh,
        String fvgStatus    // "CREATED", "TOUCHED", "FILLED" itd.
) {}
