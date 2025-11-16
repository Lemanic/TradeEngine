package pl.tradeengine.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.NotNull;

public record CandlePriceDto(
        @NotBlank String symbol,
        @NotNull Double currentHigh,
        @NotNull Double currentLow
) {}
