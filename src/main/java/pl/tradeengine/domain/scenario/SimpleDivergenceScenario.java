package pl.tradeengine.domain.scenario;

import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.DivergenceSignal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public class SimpleDivergenceScenario implements Scenario {

    @Override
    public String name() {
        return "SIMPLE_DIVERGENCE";
    }

    @Override
    public List<AlertToSend> onEvent(DomainEvent event) {
        if (!(event instanceof DivergenceDetectedEvent divergenceEvent)) {
            return List.of();
        }

        DivergenceSignal signal = divergenceEvent.signal();

        AlertToSend alert = new AlertToSend(
                signal.getSymbol(),
                signal.getDirection(),
                name(),
                signal.getTimeframe(),
                new BigDecimal(420),
                Optional.empty(),
                Optional.empty(),
                "Divergence on " + signal.getTimeframe()
        );

        return List.of(alert);
    }

    @Override
    public Long id() {
        return 0L;
    }
}
