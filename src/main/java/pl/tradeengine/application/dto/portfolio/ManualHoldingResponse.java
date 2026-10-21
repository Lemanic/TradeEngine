package pl.tradeengine.application.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.tradeengine.domain.model.portfolio.ManualHolding;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "ManualHolding", description = """
        Pozycja, której ilość podaje człowiek. Odpowiedź nie zawiera kwot ani cen — to rejestr
        tego, co wpisano; wycena dzieje się przy budowaniu snapshotu.""")
public record ManualHoldingResponse(

        @Schema(description = "Stabilny identyfikator z konfiguracji, używany w ścieżce PUT.",
                example = "tao-staking", requiredMode = Schema.RequiredMode.REQUIRED)
        String key,

        @Schema(description = "Nazwa źródła — ta sama, która pojawia się w summary.sources[].name.",
                example = "TAO staking", requiredMode = Schema.RequiredMode.REQUIRED)
        String source,

        @Schema(example = "TAO", requiredMode = Schema.RequiredMode.REQUIRED)
        String asset,

        @Schema(example = "bittensor", requiredMode = Schema.RequiredMode.REQUIRED)
        String network,

        @Schema(description = "Aktualna ilość, pełna precyzja.", example = "11.37",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount,

        @Schema(description = "Kiedy ostatnio zmieniono ilość — nie kiedy ją wyceniono.",
                example = "2026-10-06T07:31:52Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {

    public static ManualHoldingResponse from(ManualHolding holding) {
        return new ManualHoldingResponse(holding.key(), holding.source(), holding.asset(),
                holding.network(), PortfolioAmounts.plain(holding.amount()), holding.updatedAt());
    }
}
