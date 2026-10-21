package pl.tradeengine.application.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.tradeengine.domain.model.portfolio.FiatCurrency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(name = "PortfolioSummary", description = "Stan portfela z ostatniego snapshotu.")
public record PortfolioSummaryResponse(

        @Schema(description = "Moment wykonania snapshotu; snapshoty powstają o pełnych godzinach.",
                example = "2026-10-04T07:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant takenAt,

        @Schema(description = "Waluta wszystkich kwot w odpowiedzi — echo parametru zapytania.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        FiatCurrency currency,

        @Schema(description = """
                Łączna wartość portfela. Suma positions[].value może różnić się o grosze —
                to skutek zaokrąglenia do dwóch miejsc.""",
                example = "47899.88", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal total,

        @Schema(description = "false, gdy snapshot nie objął wszystkich źródeł.",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean complete,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ChangeSetDto change,

        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED,
                description = "null, gdy nie ma żadnych danych historycznych.")
        AthDto ath,

        @Schema(description = "Podział na źródła; może być pustą tablicą. Kolejność dowolna.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<SourceDto> sources,

        @Schema(description = "Pozycje; może być pustą tablicą. Bez stronicowania.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<PositionDto> positions
) {}
