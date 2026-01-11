package pl.tradeengine.application.dto;

public record BiasAlertDto(
        String symbol,
        String timeframe,
        String biasStatus
) {}
