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
        // --- NOWA LOGIKA ZARZĄDZANIA STANEM FVG ---
        PriceCandle tempCandle = mapDtoToPriceCandle(dto); // Tworzymy tymczasowy obiekt świecy
        List<FvgZone> intersectedFvgs = fvgRepository.findIntersectingOpenFvgs(
                tempCandle.symbol(), tempCandle.timeframe(), tempCandle.close()
        );

        // Jeśli cena weszła w jakiś FVG o statusie CREATED, zmień jego status na TOUCHED
        for (FvgZone fvg : intersectedFvgs) {
            fvgRepository.updateStatus(fvg.getId(), FvgStatus.TOUCHED);
            log.info("FVG status updated to TOUCHED for id: {}", fvg.getId());
        }
        // --- KONIEC NOWEJ LOGIKI ---

        // Ta część pozostaje bez zmian - wciąż wysyłamy event do silnika scenariuszy
        DomainEvent event = new PriceCandleEvent(tempCandle);
        log.info("Processing Price Update for symbol: {}", dto.symbol());
        process(event);
    }

//    public void handlePriceUpdate(PriceUpdateDto dto) {
//        DomainEvent event = mapToPriceUpdateEvent(dto);
//        process(event);
//    }
    private PriceCandle mapDtoToPriceCandle(PriceUpdateDto dto) {
        Symbol symbol = new Symbol(dto.symbol());
        Timeframe timeframe = Timeframe.fromCode(dto.timeframe());

        return new PriceCandle(
                symbol,
                timeframe,
                null,
                null,
                0.0,
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
