package pl.tradeengine.alerts.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.tradeengine.alerts.domain.Direction;
import pl.tradeengine.alerts.domain.IncomingAlert;
import pl.tradeengine.alerts.domain.SignalType;
import pl.tradeengine.alerts.infra.jpa.FvgStatus;
import pl.tradeengine.alerts.infra.jpa.FvgZoneEntity;
import pl.tradeengine.alerts.infra.jpa.FvgZoneRepository;
import pl.tradeengine.webhook.dto.CandlePriceDto;
import pl.tradeengine.webhook.dto.DivergenceAlertDto;
import pl.tradeengine.webhook.dto.FvgAlertDto;

import java.util.List;

@Service
public class AlertIngestService {

    private static final Logger log = LoggerFactory.getLogger(AlertIngestService.class);
    private final StrategyEngine strategyEngine;
    private final FvgZoneRepository fvgZoneRepository;

    public AlertIngestService(StrategyEngine strategyEngine, FvgZoneRepository fvgZoneRepository) {
        this.strategyEngine = strategyEngine;
        this.fvgZoneRepository = fvgZoneRepository;
    }

    public void handleDivergence(DivergenceAlertDto dto) {
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
                dto.fvgHigh(),
                dto.fvgStatus()
        );
        fvgZoneRepository.save(entity);
    }

    public void handleFvgTouched(FvgAlertDto dto) {
    }

    public void handleFvgFilled(FvgAlertDto dto) {
    }

    public void updateFvgStatus(CandlePriceDto dto) {
        List<FvgZoneEntity> activeFvgs = fvgZoneRepository.findBySymbolAndActiveIsTrue(dto.symbol());

        for (FvgZoneEntity fvg : activeFvgs) {
            boolean isTouched = dto.currentHigh() >= fvg.getFvgLow() && dto.currentLow() <= fvg.getFvgHigh();
            boolean isFilled = false;

            // Przykład warunku fill dla LONG i SHORT (dostosuj wg strategii)
            if (fvg.getDirection() == Direction.LONG) {
                isFilled = dto.currentLow() < fvg.getFvgLow();
            } else if (fvg.getDirection() == Direction.SHORT) {
                isFilled = dto.currentHigh() > fvg.getFvgHigh();
            }

            if (isFilled && fvg.isActive()) {
                fvg.setStatus(FvgStatus.FILLED);
                fvg.setActive(false);
                fvgZoneRepository.save(fvg);
                log.info("FVG filled for symbol {} at range [{}, {}]", fvg.getSymbol(), fvg.getFvgLow(), fvg.getFvgHigh());
            } else if (isTouched && fvg.isActive() && fvg.getStatus() == FvgStatus.CREATED) {
                fvg.setStatus(FvgStatus.TOUCHED);
                fvgZoneRepository.save(fvg);
                log.info("FVG touched for symbol {} at range [{}, {}]", fvg.getSymbol(), fvg.getFvgLow(), fvg.getFvgHigh());
            }
        }
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
