package pl.tradeengine.application.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "Ath", description = """
        Historyczne maksimum w walucie zapytania. Liczone z zapisanych totali tej waluty,
        więc szczyt w PLN może wypaść innego dnia niż w USD.""")
public record AthDto(

        @Schema(description = "Wartość szczytu.", example = "52213.54",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal value,

        @Schema(description = "Moment szczytu.", example = "2026-05-07T05:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant at
) {}
