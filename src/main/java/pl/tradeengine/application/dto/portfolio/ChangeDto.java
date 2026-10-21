package pl.tradeengine.application.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "Change", description = "Zmiana wartości portfela wobec wcześniejszego snapshotu.")
public record ChangeDto(

        @Schema(description = "Zmiana kwotowa w walucie zapytania, ujemna przy spadku.",
                example = "349.55", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal abs,

        @Schema(description = "Zmiana względna w punktach procentowych: 0.7351 znaczy +0,7351 %.",
                example = "0.7351", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal pct
) {}
