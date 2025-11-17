package pl.tradeengine.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pl.tradeengine.alerts.domain.Direction;
import pl.tradeengine.alerts.domain.SignalType;
import pl.tradeengine.alerts.infra.jpa.FvgStatus;

public record FvgAlertDto(
        @NotBlank String symbol,
        @NotBlank String timeframe,
        @NotNull SignalType signalType,
        @NotNull Direction direction,
        @NotNull Double fvgLow,
        @NotNull Double fvgHigh,
        @NotNull FvgStatus fvgStatus
) {}