package pl.tradeengine.application.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "HistoryPoint", description = "Jeden punkt szeregu czasowego.")
public record HistoryPointDto(

        @Schema(description = "Czas snapshotu, z którego pochodzi punkt — nie początek przedziału.",
                example = "2026-10-04T07:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant t,

        @Schema(description = "Wartość portfela w tym snapshocie.", example = "47899.88",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal value,

        @Schema(description = "false, gdy ten snapshot nie objął wszystkich źródeł.",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean complete
) {}
