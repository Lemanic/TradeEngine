package pl.tradeengine.domain.scenario;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.model.*;
import pl.tradeengine.domain.port.DivergenceRepository;
import pl.tradeengine.domain.port.FvgRepository;

import java.util.List;
import java.util.Optional;

@Slf4j
//@Component
public class KeyLevelDivergenceScenario implements Scenario {

    private final FvgRepository fvgRepository;
    private final DivergenceRepository divergenceRepository;

    public KeyLevelDivergenceScenario(FvgRepository fvgRepository, DivergenceRepository divergenceRepository) {
        this.fvgRepository = fvgRepository;
        this.divergenceRepository = divergenceRepository;
    }

    @Override
    public String name() {
        return "SIMPLE_FVG_DIVERGENCE_STRATEGY";
    }

    @Override
    public List<AlertToSend> onEvent(DomainEvent event) {
        if (!(event instanceof DivergenceDetectedEvent divergenceEvent)) {
            return List.of();
        }

        DivergenceSignal divergence = divergenceEvent.signal();
        List<FvgZone> touchedFvgs = fvgRepository.findTouched(
                divergence.getSymbol(), divergence.getTimeframe()
        );

        Optional<FvgZone> matchingFvgOpt = touchedFvgs.stream()
                .filter(fvg -> fvg.getDirection() == divergence.getDirection())
                .findFirst();

        if (matchingFvgOpt.isEmpty()) {
            log.info("Divergence {} detected, but no matching FVG was found. No alert.", divergence.getDirection());
            return List.of();
        }

        FvgZone matchingFvg = matchingFvgOpt.get();

        String description = String.format(
                "ALERT: Wykryto dywergencję %s po dotknięciu FVG [%.2f-%.2f]!",
                divergence.getDirection(), matchingFvg.getLowerPrice(), matchingFvg.getUpperPrice()
        );

        AlertToSend alert = new AlertToSend(
                divergence.getSymbol(),
                divergence.getDirection(),
                name(),
                divergence.getTimeframe(),
                matchingFvg.getUpperPrice(),
                Optional.empty(),
                Optional.empty(),
                description
        );

        fvgRepository.updateStatus(matchingFvg.getId(), FvgStatus.CONSUMED);
        log.info("FVG with id {} has been consumed to generate an alert.", matchingFvg.getId());

        return List.of(alert);
    }

    @Override
    public Long id() {
        return 0L;
    }
}
