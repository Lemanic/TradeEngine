package pl.tradeengine.application.sevice;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.tradeengine.application.dto.DivergenceAlertDto;
import pl.tradeengine.application.dto.FvgAlertDto;
import pl.tradeengine.application.dto.PriceUpdateDto;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.event.FvgCreatedEvent;
import pl.tradeengine.domain.event.PriceCandleEvent;
import pl.tradeengine.domain.model.*;
import pl.tradeengine.domain.port.DivergenceRepository;
import pl.tradeengine.domain.port.FvgRepository;
import pl.tradeengine.domain.scenario.ScenarioEngine;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
public class WebhookProcessingService {
    private final ScenarioEngine scenarioEngine;
    private final AlertDispatchService alertDispatchService;
    private final FvgRepository fvgRepository;
    private final DivergenceRepository divergenceRepository;

    public WebhookProcessingService(ScenarioEngine scenarioEngine,
                                    AlertDispatchService alertDispatchService,
                                    FvgRepository fvgRepository,
                                    DivergenceRepository divergenceRepository) {
        this.scenarioEngine = scenarioEngine;
        this.alertDispatchService = alertDispatchService;
        this.fvgRepository = fvgRepository;
        this.divergenceRepository = divergenceRepository;
    }

    public void handleFvg(FvgAlertDto dto) {
        DomainEvent event = mapToFvgCreatedEvent(dto);
        log.info(dto.symbol() + ", " + dto.direction() + ", " + dto.signalType() + "," + dto.timeframe());
        process(event);
    }

    public void handleDivergence(DivergenceAlertDto dto) {
        DomainEvent event = mapToDivergenceDetectedEvent(dto);
        log.info(dto.symbol() + ", " + dto.direction() + ", " + dto.signalType() + "," + dto.timeframe());
        process(event);
    }

    public void handlePriceUpdate(PriceUpdateDto dto) {
        DomainEvent event = mapToPriceUpdateEvent(dto);
        log.info(dto.symbol() + ", " + dto.currentLow() + dto.currentHigh() + ", " + dto.signalType() + "," + dto.timeframe());
        process(event);
    }

    private void process(DomainEvent event) {
        List<AlertToSend> alerts = scenarioEngine.onEvent(event);
        alertDispatchService.dispatch(alerts);
    }

    private DomainEvent mapToFvgCreatedEvent(FvgAlertDto dto) {
        Symbol symbol = new Symbol(dto.symbol());
        Timeframe timeframe = Timeframe.fromCode(dto.timeframe());
        Direction direction = Direction.fromSignal(dto.direction());
        FvgStatus status = FvgStatus.valueOf(dto.fvgStatus());

        FvgZone fvgZone = new FvgZone(
                null,
                symbol,
                timeframe,
                direction,
                dto.fvgLow(),
                dto.fvgHigh(),
                FvgKind.FVG,
                status
        );

        return new FvgCreatedEvent(fvgZone);
    }

    private DomainEvent mapToDivergenceDetectedEvent(DivergenceAlertDto dto) {
        Symbol symbol = new Symbol(dto.symbol());
        Timeframe timeframe = Timeframe.fromCode(dto.timeframe());
        Direction direction = Direction.fromSignal(dto.direction());
        double strength = 0.0; // TODO
        DivergenceSignal signal = new DivergenceSignal(
                null,
                symbol,
                timeframe,
                direction,
                strength,
                ZonedDateTime.now()
        );

        return new DivergenceDetectedEvent(signal);
    }

    private DomainEvent mapToPriceUpdateEvent(PriceUpdateDto dto) {
        Symbol symbol = new Symbol(dto.symbol());
        Timeframe timeframe = Timeframe.fromCode(dto.timeframe());

        PriceCandle candle = new PriceCandle(
                symbol,
                timeframe,
                null,
                null,
                0.0,
                dto.currentHigh(),
                dto.currentLow(),
                0.0
        );

        return new PriceCandleEvent(candle);
    }


//    private DomainEvent mapToFvgCreatedEvent(FvgAlertDto dto) {
//        // budujesz FvgZone
//        FvgZone fvgZone = new FvgZone(...);
//        FvgZone persisted = fvgRepository.save(fvgZone);
//        return new FvgCreatedEvent(persisted);
//    }

//    private DomainEvent mapToDivergenceDetectedEvent(DivergenceAlertDto dto) {
//        DivergenceSignal signal = new DivergenceSignal(...);
//        DivergenceSignal persisted = divergenceRepository.save(signal);
//        return new DivergenceDetectedEvent(persisted);
//    }


}
