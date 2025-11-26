package pl.tradeengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import pl.tradeengine.domain.model.Timeframe;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public class StrategyProperties {

    private List<StrategyConfig> strategies;

    public List<StrategyConfig> getStrategies() {
        return strategies;
    }

    public void setStrategies(List<StrategyConfig> strategies) {
        this.strategies = strategies;
    }

    @Getter
    @Setter
    public static class StrategyConfig {
        private String name;
        private boolean enabled;
        private List<Timeframe> timeframes;
    }
}