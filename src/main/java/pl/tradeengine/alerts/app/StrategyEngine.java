package pl.tradeengine.alerts.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.tradeengine.alerts.domain.IncomingAlert;
import pl.tradeengine.alerts.domain.SignalType;
import pl.tradeengine.alerts.infra.jpa.FvgZoneEntity;
import pl.tradeengine.alerts.infra.jpa.FvgZoneRepository;

import java.util.List;

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

        // niech sprawdza czy to jest najwyższy fvg?
        List<FvgZoneEntity> fvgs = fvgZoneRepository.findBySymbol(alert.symbol());
        // tutaj posortować?

        System.out.println();
        FvgZoneEntity highestFvg;
        for (FvgZoneEntity fvg : fvgs) {
            // sortowanie?

        }


        if (alert.strength() >= 3.0) {
            log.info("DIVERGENCE signal accepted by StrategyEngine: {}", alert);
            // TODO: tu w przyszłości: zapis decyzji, powiadomienie, integracja z FVG, itp.
        } else {
            log.info("DIVERGENCE signal ignored (too weak): {}", alert);
//            log.debug("DIVERGENCE signal ignored (too weak): {}", alert);
        }
    }
}
