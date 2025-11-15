package pl.tradeengine.webhook.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pl.tradeengine.alerts.domain.SignalType;

public record FvgAlertDto(
        @NotBlank String symbol,
        @NotBlank String timeframe,
        @NotBlank SignalType signalType,
        @NotBlank String direction,
        @NotNull Double strength,
        @NotNull Double fvgLow,
        @NotNull Double fvgHigh
) {}