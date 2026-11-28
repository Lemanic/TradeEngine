package pl.tradeengine.application.dto.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.tradeengine.application.service.AlertMuteService.StrategyView;
import pl.tradeengine.domain.model.Timeframe;

import java.util.List;

@Schema(name = "Strategy", description = "Wpis z app.strategies razem z jego wyciszeniami.")
public record StrategyDto(

        @Schema(example = "GRINDER_SWING_D1_H1", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "Z YAML-a. Wyłączona strategia nie wysyła nic, wyciszenia nie mają "
                + "wtedy znaczenia — ale zostają i zadziałają po jej włączeniu.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean enabled,

        @Schema(description = "Interwały z YAML-a — tylko na nich strategia dostaje dywergencje.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<Timeframe> timeframes,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<AlertMuteDto> mutes
) {

    public static StrategyDto from(StrategyView view) {
        return new StrategyDto(view.name(), view.enabled(), view.timeframes(),
                view.mutes().stream().map(AlertMuteDto::from).toList());
    }
}
