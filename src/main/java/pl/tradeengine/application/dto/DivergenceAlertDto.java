package pl.tradeengine.application.dto;

public record DivergenceAlertDto(
        String symbol,
        String timeframe,    // "H4"
        String signalType,   // "DIVERGENCE"
        String direction     // "LONG" / "SHORT"
        // opcjonalnie: String strength;
) {}