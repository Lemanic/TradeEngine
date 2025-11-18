package pl.tradeengine.application.dto;

public record DivergenceAlertDto(
        String symbol,
        String timeframe,
        String signalType,
        String direction
) {}