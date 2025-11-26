package pl.tradeengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import pl.tradeengine.domain.model.Timeframe;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.scenarios.double-divergence")
public class DoubleDivergenceScenarioProperties {
    private boolean enabled;
    private List<Timeframe> timeframes;
}
