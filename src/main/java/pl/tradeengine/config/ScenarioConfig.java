package pl.tradeengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.tradeengine.domain.scenario.Scenario;
import pl.tradeengine.domain.scenario.ScenarioEngine;
import pl.tradeengine.domain.scenario.SimpleDivergenceScenario;

import java.util.List;

@Configuration
public class ScenarioConfig {

//    @Bean
//    public Scenario simpleDivergenceScenario() {
//        return new SimpleDivergenceScenario();
//    }

    @Bean
    public ScenarioEngine scenarioEngine(List<Scenario> scenarios) {
        return new ScenarioEngine(scenarios);
    }
}

