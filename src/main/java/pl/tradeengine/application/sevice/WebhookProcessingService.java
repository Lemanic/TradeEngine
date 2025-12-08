package pl.tradeengine.application.sevice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.tradeengine.application.dto.DivergenceAlertDto;
import pl.tradeengine.application.dto.FvgAlertDto;
import pl.tradeengine.application.dto.PriceUpdateDto;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.event.FvgCreatedEvent;
import pl.tradeengine.domain.event.FvgTouchedEvent;
import pl.tradeengine.domain.event.PriceCandleEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.model.FvgKind;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.DivergenceRepository;
import pl.tradeengine.domain.port.FvgRepository;
import pl.tradeengine.domain.scenario.ScenarioEngine;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
public class WebhookProcessingService {
    private final ScenarioEngine scenarioEngine;
    private final AlertDispatchService alertDispatchService;
    private final FvgRepository fvgRepository;
    private final DivergenceRepository divergenceRepository;

    public WebhookProcessingService(ScenarioEngine scenarioEngine, AlertDispatchService alertDispatchService, FvgRepository fvgRepository, DivergenceRepository divergenceRepository) {
        this.scenarioEngine = scenarioEngine;
        this.alertDispatchService = alertDispatchService;
        this.fvgRepository = fvgRepository;
        this.divergenceRepository = divergenceRepository;
    }

    public void handleFvg(FvgAlertDto dto) {
        FvgZone fvgZone = mapDtoToFvgZone(dto);

        FvgZone savedFvg = fvgRepository.save(fvgZone);

        DomainEvent event = new FvgCreatedEvent(savedFvg);

        log.info("Saved FVG id={}, symbol={}, tf={}", savedFvg.getId(), savedFvg.getSymbol().code(), savedFvg.getTimeframe());
        process(event);

}

    public void handleDivergence(DivergenceAlertDto dto) {
        DivergenceSignal signal = mapDtoToDivergenceSignal(dto);

        DivergenceSignal savedSignal = divergenceRepository.save(signal);

        DomainEvent event = new DivergenceDetectedEvent(savedSignal);

        log.info("Saved Divergence id={}, symbol={}, tf={}", savedSignal.getId(), savedSignal.getSymbol().code(), savedSignal.getTimeframe());
        process(event);

    }

    public void handlePriceUpdate(PriceUpdateDto dto) {
        PriceCandle tempCandle = mapDtoToPriceCandle(dto);

        List<FvgZone> intersectedFvgs = fvgRepository.findIntersectingOpenFvgs(
                tempCandle.symbol(), tempCandle.low(), tempCandle.high()
        );

        for (FvgZone fvg : intersectedFvgs) {
            if (fvg.getStatus() == FvgStatus.FILLED || fvg.getStatus() == FvgStatus.CONSUMED) {
                continue;
            }

            boolean isFilled = false;

            if (fvg.getDirection() == Direction.LONG && tempCandle.low().compareTo(fvg.getLowerPrice()) <= 0) {
                isFilled = true;
            } else if (fvg.getDirection() == Direction.SHORT && tempCandle.high().compareTo(fvg.getUpperPrice()) >= 0) {
                isFilled = true;
            }

            if (isFilled) {
                fvgRepository.updateStatus(fvg.getId(), FvgStatus.FILLED);
                log.info("FVG status updated to FILLED for id: {}", fvg.getId());
            } else {
                if (fvg.getStatus() == FvgStatus.CREATED) {
                    fvgRepository.updateStatus(fvg.getId(), FvgStatus.TOUCHED);
                    log.info("FVG status updated to TOUCHED for id: {}", fvg.getId());

                    FvgZone touchedFvg = new FvgZone(
                            fvg.getId(),
                            fvg.getSymbol(),
                            fvg.getTimeframe(),
                            fvg.getDirection(),
                            fvg.getLowerPrice(),
                            fvg.getUpperPrice(),
                            fvg.getStrength(),
                            fvg.getKind(),
                            FvgStatus.TOUCHED
                    );

                    DomainEvent fvgTouchedEvent = new FvgTouchedEvent(touchedFvg, ZonedDateTime.now());
                    log.info("Emitting FvgTouchedEvent for FVG id={}", touchedFvg.getId());
                    process(fvgTouchedEvent);
                }
            }
        }

        DomainEvent event = new PriceCandleEvent(tempCandle);
        log.info("Processing Price Update for symbol: {}", dto.symbol()); // Hide it?
        process(event);
    }

    private PriceCandle mapDtoToPriceCandle(PriceUpdateDto dto) {
        Symbol symbol = new Symbol(dto.symbol());
        Timeframe timeframe = Timeframe.fromCode(dto.timeframe());

        return new PriceCandle(
                symbol,
                timeframe,
                null,
                null,
                new BigDecimal(69),
                dto.currentHigh(),
                dto.currentLow(),
                dto.close()
    );
    }

    private void process(DomainEvent event) {
        List<AlertToSend> alerts = scenarioEngine.onEvent(event);
        alertDispatchService.dispatch(alerts);
    }

    private FvgZone mapDtoToFvgZone(FvgAlertDto dto) {
        Symbol symbol = new Symbol(dto.symbol());
        Timeframe timeframe = Timeframe.fromCode(dto.timeframe());
        Direction direction = Direction.fromSignal(dto.direction());
        FvgStatus status = FvgStatus.valueOf(dto.fvgStatus());

        return new FvgZone(
                null,
                symbol,
                timeframe,
                direction,
                dto.fvgLow(),
                dto.fvgHigh(),
                null,
                FvgKind.FVG,
                status
        );
    }

    private DivergenceSignal mapDtoToDivergenceSignal(DivergenceAlertDto dto) {
        Symbol symbol = new Symbol(dto.symbol());
        Timeframe timeframe = Timeframe.fromCode(dto.timeframe());
        Direction direction = Direction.fromSignal(dto.direction());
        double strength = calculateStrengthBasedOnContext(dto);

        return new DivergenceSignal(
                null,
                symbol,
                timeframe,
                direction,
                strength,
                ZonedDateTime.now()
        );
    }

    private double calculateStrengthBasedOnContext(DivergenceAlertDto dto) {
        return 21.37;
    }

}
