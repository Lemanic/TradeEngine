package pl.tradeengine.alerts.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.tradeengine.alerts.domain.IncomingAlert;
import pl.tradeengine.alerts.domain.SignalType;
import pl.tradeengine.alerts.infra.jpa.FvgStatus;
import pl.tradeengine.alerts.infra.jpa.FvgZoneEntity;
import pl.tradeengine.alerts.infra.jpa.FvgZoneRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class StrategyEngine {

    private static final Logger log = LoggerFactory.getLogger(StrategyEngine.class);
    private FvgZoneRepository fvgZoneRepository;

    public StrategyEngine(FvgZoneRepository fvgZoneRepository) {
        this.fvgZoneRepository = fvgZoneRepository;
    }

    public void process(IncomingAlert alert) {
        if (alert.signalType() != SignalType.DIVERGENCE) {
            return;
        }

        List<FvgZoneEntity> fvgs = fvgZoneRepository.findBySymbol(alert.symbol());

        Map<String, Optional<FvgZoneEntity>> highestFvgByTimeframe = fvgs.stream()
                .collect(Collectors.groupingBy(
                        FvgZoneEntity::getTimeframe,
                        Collectors.maxBy(Comparator.comparingDouble(FvgZoneEntity::getFvgHigh))
                ));

        Optional<FvgZoneEntity> highestStrengthFvg = highestFvgByTimeframe.values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(fvg -> fvg.getStatus() != FvgStatus.CREATED)
                .max(Comparator.comparingDouble(FvgZoneEntity::getStrength));

        if (highestStrengthFvg.isPresent()) {
            // filled /= touch siła
            System.out.println("div siła: " + alert.strength() + "fvg siła:" + highestStrengthFvg.get().getStrength());
        }

        log.info("DIVERGENCE signal accepted by StrategyEngine: {}", alert);
    }
}
