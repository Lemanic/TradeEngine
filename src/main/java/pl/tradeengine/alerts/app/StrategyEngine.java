package pl.tradeengine.alerts.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.tradeengine.alerts.domain.IncomingAlert;
import pl.tradeengine.alerts.domain.SignalType;

@Service
public class StrategyEngine {

    private static final Logger log = LoggerFactory.getLogger(StrategyEngine.class);

    public void process(IncomingAlert alert) {
        if (alert.signalType() != SignalType.DIVERGENCE) {
            return;
        }

        // Przykład: loguj tylko dywergencje z H1+ (strength >= 3.0)
        if (alert.strength() >= 3.0) {
            log.info("DIVERGENCE signal accepted by StrategyEngine: {}", alert);
            // TODO: tu w przyszłości: zapis decyzji, powiadomienie, integracja z FVG, itp.
        } else {
            log.info("DIVERGENCE signal ignored (too weak): {}", alert);
//            log.debug("DIVERGENCE signal ignored (too weak): {}", alert);
        }
    }
}
