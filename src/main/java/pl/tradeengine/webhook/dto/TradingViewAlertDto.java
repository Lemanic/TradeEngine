package pl.tradeengine.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.NotNull;
import pl.tradeengine.alerts.domain.Direction;
import pl.tradeengine.alerts.domain.SignalType;

public record TradingViewAlertDto(
        @NotBlank String symbol,
        @NotBlank String interval,
        @NotNull SignalType signalType,
        @NotNull Direction direction,
        @NotNull Double strength
) {}
