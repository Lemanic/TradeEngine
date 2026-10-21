package pl.tradeengine.application.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "Position", description = "Jedna pozycja portfela, wyceniona w walucie zapytania.")
public record PositionDto(

        @Schema(description = "Odpowiada jednej z nazw w sources[].name.", example = "Rabby",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String source,

        @Schema(description = "Sieć, wyświetlana dosłownie.", example = "ethereum",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String network,

        @Schema(description = "Symbol aktywa.", example = "ETH",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String asset,

        @Schema(description = "Ilość w pełnej precyzji — bez zaokrąglania.",
                example = "2.41", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount,

        @Schema(description = "Cena jednostkowa.", example = "3735.0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal price,

        @Schema(description = "amount × price.", example = "9001.35",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal value,

        @Schema(description = "Udział w portfelu w punktach procentowych.", example = "18.792",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal pct,

        @Schema(description = """
                true, gdy pozycja nie została odświeżona w tym snapshocie: ilość przeniesiono
                z poprzedniego albo cena pochodzi z ostatniej znanej.""",
                example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean stale
) {}
