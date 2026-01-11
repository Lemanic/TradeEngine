package pl.tradeengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.BiasRepository;
import pl.tradeengine.domain.port.FvgRepository;
import pl.tradeengine.domain.port.SwingPointRepository;
import pl.tradeengine.domain.scenario.GrinderStrategyScenario;
import pl.tradeengine.domain.scenario.Scenario;

import java.util.List;

@Configuration
public class GrinderStrategyConfig {

    //Swing Trading// Bias: D1 -> Trigger: H1 -> FVG: H4, D1
    @Bean
    public Scenario grinderSwingStrategy(FvgRepository fvgRepo, BiasRepository biasRepo, SwingPointRepository swingRepo) {
        return new GrinderStrategyScenario(
                fvgRepo, biasRepo, swingRepo,
                "GRINDER_SWING_D1_H1",
                Timeframe.D1,
                List.of(Timeframe.H4, Timeframe.D1), Timeframe.H1);
    }

    //SPOT Trading// Bias: W1 -> Trigger: H4 -> FVG: D1, W1
    @Bean
    public Scenario grinderPositionStrategy(FvgRepository fvgRepo, BiasRepository biasRepo, SwingPointRepository swingRepo) {
        return new GrinderStrategyScenario(
                fvgRepo, biasRepo, swingRepo,
                "GRINDER_POSITION_W1_H4",
                Timeframe.W1,
                List.of(Timeframe.D1, Timeframe.W1), Timeframe.H4);
    }
}
