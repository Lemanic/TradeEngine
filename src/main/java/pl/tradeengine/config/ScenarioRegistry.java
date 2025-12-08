package pl.tradeengine.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.scenario.Scenario;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ScenarioRegistry {

    private final Map<String, Scenario> scenariosByName;
    private final StrategyProperties strategyProperties;

    public ScenarioRegistry(List<Scenario> scenarios, StrategyProperties strategyProperties) {
        this.strategyProperties = strategyProperties;
        this.scenariosByName = scenarios.stream()
                .collect(Collectors.toMap(Scenario::name, Function.identity()));
    }

    public List<Scenario> getScenariosFor(Timeframe timeframe) {
        return strategyProperties.getStrategies().stream()
                .filter(StrategyProperties.StrategyConfig::isEnabled)
                .filter(cfg -> cfg.getTimeframes().contains(timeframe))
                .map(cfg -> scenariosByName.get(cfg.getName()))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<Scenario> getAllEnabledScenarios() {
        return strategyProperties.getStrategies().stream()
                .filter(StrategyProperties.StrategyConfig::isEnabled)
                .map(cfg -> scenariosByName.get(cfg.getName()))
                .filter(Objects::nonNull)
                .toList();
    }


    @PostConstruct
    void logConfig() {
        log.info("Configured strategies (from YAML):");
        strategyProperties.getStrategies().forEach(cfg ->
                log.info(" - name={}, enabled={}, timeframes={}",
                        cfg.getName(), cfg.isEnabled(), cfg.getTimeframes())
        );
    }

}