package pl.tradeengine.domain.scenario;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.DivergenceRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class DoubleDivergenceScenario implements Scenario {

    private static final int CANDLE_LOOKBACK_WINDOW = 35;
    private final DivergenceRepository divergenceRepository;

    public DoubleDivergenceScenario(DivergenceRepository divergenceRepository) {
        this.divergenceRepository = divergenceRepository;
    }

    @Override
    public String name() {
        return "DOUBLE_DIVERGENCE_STRATEGY";
    }

    @Override
    public List<AlertToSend> onEvent(DomainEvent event) {
        if (!(event instanceof DivergenceDetectedEvent newDivergenceEvent)) {
            return List.of();
        }

        DivergenceSignal newDivergence = newDivergenceEvent.signal();
        ZonedDateTime lookbackTime = calculateLookbackTime(newDivergence.getTimeframe(), CANDLE_LOOKBACK_WINDOW);

        List<DivergenceSignal> recentDivergences = divergenceRepository.findAllByDirectionSince(
                newDivergence.getSymbol(),
                newDivergence.getTimeframe(),
                newDivergence.getDirection(),
                lookbackTime
        );

        if (recentDivergences.size() > 1) {
            log.info("Double Divergence detected! Signal count: {}, Symbol: {}, Timeframe: {}",
                    recentDivergences.size(), newDivergence.getSymbol().code(), newDivergence.getTimeframe());

            String description = String.format(
                    "DoubleDiv | %s | %s | TF %s | SCORE %s/10 | x%s",
                    newDivergence.getSymbol().code(),
                    newDivergence.getDirection(),
                    newDivergence.getTimeframe(),
                    6.9,
                    recentDivergences.size()
            );

            AlertToSend alert = new AlertToSend(
                    newDivergence.getSymbol(),
                    newDivergence.getDirection(),
                    name(),
                    newDivergence.getTimeframe(),
                    new BigDecimal(21.37),
                    Optional.empty(),
                    Optional.empty(),
                    description
            );
            return List.of(alert);
        }

        return List.of();
    }

    @Override
    public Long id() {
        return 0L;
    }

    private ZonedDateTime calculateLookbackTime(Timeframe timeframe, int numberOfCandles) {
        Duration totalLookback = timeframe.getDuration().multipliedBy(numberOfCandles);
        return ZonedDateTime.now().minus(totalLookback);
    }

}
