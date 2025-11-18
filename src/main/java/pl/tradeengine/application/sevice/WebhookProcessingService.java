package pl.tradeengine.application.sevice;

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

        process(event);
}

    public void handleDivergence(DivergenceAlertDto dto) {
        DivergenceSignal signal = mapDtoToDivergenceSignal(dto);

        DivergenceSignal savedSignal = divergenceRepository.save(signal);

        DomainEvent event = new DivergenceDetectedEvent(savedSignal);

        process(event);
    }

    public void handlePriceUpdate(PriceUpdateDto dto) {
        DomainEvent event = mapToPriceUpdateEvent(dto);
        process(event);
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

//        return new FvgZone(
//                null,
//                symbol,
//                timeframe,
//                direction,
//                dto.fvgLow(),
//                dto.fvgHigh(),
//                FvgKind.FVG,  // <-- Poprawny parametr dla 'kind'
//                status        // <-- Poprawny parametr dla 'status'
//        );
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

}
