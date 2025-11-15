package pl.tradeengine.alerts.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.tradeengine.alerts.domain.IncomingAlert;
import pl.tradeengine.alerts.domain.SignalType;
import pl.tradeengine.alerts.infra.jpa.FvgZoneEntity;
import pl.tradeengine.alerts.infra.jpa.FvgZoneRepository;
import pl.tradeengine.webhook.dto.FvgAlertDto;
import pl.tradeengine.webhook.dto.TradingViewAlertDto;

@Service
public class AlertIngestService {

    private static final Logger log = LoggerFactory.getLogger(AlertIngestService.class);
    private final StrategyEngine strategyEngine;
    private final FvgZoneRepository fvgZoneRepository;

    public AlertIngestService(StrategyEngine strategyEngine, FvgZoneRepository fvgZoneRepository) {
        this.strategyEngine = strategyEngine;
        this.fvgZoneRepository = fvgZoneRepository;
    }

    public void handle(TradingViewAlertDto dto) {
        double strength = computeStrengthFromInterval(dto.timeframe(), dto.signalType());

        IncomingAlert alert = new IncomingAlert(
                dto.symbol(),
                dto.timeframe(),
                dto.signalType(),
                dto.direction(),
                strength
        );

        log.info("IncomingAlert: {}", alert);

        strategyEngine.process(alert);
    }

    public void handleFvgCreate(FvgAlertDto dto) {
        FvgZoneEntity entity = new FvgZoneEntity(
                dto.symbol(),
                dto.timeframe(),
                dto.direction(),
                dto.strength(),
                dto.fvgLow(),
                dto.fvgHigh()
        );
        fvgZoneRepository.save(entity);
    }


    private double computeStrengthFromInterval(String timeframe, SignalType type) {
        return switch (timeframe) {
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
