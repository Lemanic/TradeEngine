package pl.tradeengine.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.NotNull;
import pl.tradeengine.alerts.domain.Direction;
import pl.tradeengine.alerts.domain.SignalType;

public record DivergenceAlertDto(
        @NotBlank String symbol,
        @NotBlank String timeframe,
        @NotNull SignalType signalType,
        @NotNull Direction direction
) {}