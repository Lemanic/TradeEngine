package pl.tradeengine.application.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "SourceShare", description = "Udział jednego źródła w portfelu.")
public record SourceDto(

        @Schema(description = "Nazwa źródła — stabilny identyfikator, UI wiąże z nią kolor.",
                example = "Rabby", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "Suma wartości pozycji tego źródła.", example = "20198.58",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal value,

        @Schema(description = "Udział w portfelu w punktach procentowych.", example = "42.1683",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal pct
) {}
