package pl.tradeengine.alerts.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.tradeengine.alerts.domain.IncomingAlert;
import pl.tradeengine.alerts.domain.SignalType;
import pl.tradeengine.webhook.dto.TradingViewAlertDto;

@Service
public class AlertIngestService {

    private static final Logger log = LoggerFactory.getLogger(AlertIngestService.class);
    private final StrategyEngine strategyEngine;

    public AlertIngestService(StrategyEngine strategyEngine) {
        this.strategyEngine = strategyEngine;
    }

    public void handle(TradingViewAlertDto dto) {
        double strength = computeStrengthFromInterval(dto.interval(), dto.signalType());

        IncomingAlert alert = new IncomingAlert(
                dto.symbol(),
                dto.interval(),
                dto.signalType(),
                dto.direction(),
                strength
        );

        log.info("IncomingAlert: {}", alert);

        strategyEngine.process(alert);
    }

    private double computeStrengthFromInterval(String interval, SignalType type) {
        return switch (interval) {
            case "M5"  -> 1.0;
            case "M15" -> 2.0;
            case "H1"  -> 3.0;
            case "H4"  -> 4.0;
            case "H12" -> 5.0;
            case "D1"  -> 6.0;
            case "D2" -> 7.0;
            case "W1"  -> 8.0;
            default    -> 0.0;
        };
    }

}
