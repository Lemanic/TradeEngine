package pl.tradeengine.application.dto;

public record MomentumAlertDto(
        String symbol,
        String timeframe,
        String signal,    // "MOMENTUM_WAVE"
        String direction  // "BULLISH", "BEARISH"
) {}
