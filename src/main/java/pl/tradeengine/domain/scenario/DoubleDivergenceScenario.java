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
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
public class DoubleDivergenceScenario implements Scenario {

    private static final int CANDLE_LOOKBACK_WINDOW = 35;

    /**
     * Timeframes on which a single divergence already fires an alert — no lookback query and no
     * second signal required. On HTF one divergence carries enough weight on its own, and waiting
     * for a pair inside the {@link #CANDLE_LOOKBACK_WINDOW} would mean waiting over a month on D1.
     */
    private static final Set<Timeframe> SINGLE_SIGNAL_TIMEFRAMES = EnumSet.of(Timeframe.H12, Timeframe.D1);

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

        if (SINGLE_SIGNAL_TIMEFRAMES.contains(newDivergence.getTimeframe())) {
            log.info("Single HTF Divergence detected! Symbol: {}, Timeframe: {}",
                    newDivergence.getSymbol().code(), newDivergence.getTimeframe());
            return List.of(buildAlert(newDivergence, "SingleDiv", 1));
        }

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
            return List.of(buildAlert(newDivergence, "DoubleDiv", recentDivergences.size()));
        }

        return List.of();
    }

    private AlertToSend buildAlert(DivergenceSignal divergence, String label, int signalCount) {
        String description = String.format(
                "%s | %s | %s | TF %s | SCORE %s/10 | x%s",
                label,
                divergence.getSymbol().code(),
                divergence.getDirection(),
                divergence.getTimeframe(),
                6.9,
                signalCount
        );

        return new AlertToSend(
                divergence.getSymbol(),
                divergence.getDirection(),
                name(),
                divergence.getTimeframe(),
                new BigDecimal(21.37),
                Optional.empty(),
                Optional.empty(),
                description,
                ZonedDateTime.now()
        );
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
