package pl.tradeengine.application.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.tradeengine.domain.model.portfolio.FiatCurrency;
import pl.tradeengine.domain.model.portfolio.HistoryInterval;
import pl.tradeengine.domain.model.portfolio.HistoryRange;

import java.util.List;

@Schema(name = "PortfolioHistory", description = "Szereg czasowy wartości portfela.")
public record PortfolioHistoryResponse(

        @Schema(description = "Echo parametru zapytania.", requiredMode = Schema.RequiredMode.REQUIRED)
        HistoryRange range,

        @Schema(description = "Rozdzielczość próbkowania, dobrana do zakresu.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        HistoryInterval interval,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        FiatCurrency currency,

        @Schema(description = """
                Punkty posortowane rosnąco po t. Puste przedziały są pomijane, więc odstępy
                nie muszą być równe, a tablica może być pusta.""",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<HistoryPointDto> points
) {}
