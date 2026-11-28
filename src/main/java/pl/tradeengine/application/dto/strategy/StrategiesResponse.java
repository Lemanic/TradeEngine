package pl.tradeengine.application.dto.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.tradeengine.application.service.AlertMuteService.Overview;
import pl.tradeengine.domain.model.Symbol;

import java.util.List;

@Schema(name = "Strategies")
public record StrategiesResponse(

        @Schema(description = "Pary z webhooków z ostatnich 30 dni plus każda wyciszona, "
                + "alfabetycznie — wiersze macierzy strategie × pary.",
                example = "[\"BTCUSDT\", \"NQ1!\", \"TAOUSDT\"]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> symbols,

        @Schema(description = "W kolejności z YAML-a.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<StrategyDto> strategies
) {

    public static StrategiesResponse from(Overview overview) {
        return new StrategiesResponse(
                overview.symbols().stream().map(Symbol::code).toList(),
                overview.strategies().stream().map(StrategyDto::from).toList());
    }
}
