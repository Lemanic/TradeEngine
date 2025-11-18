package pl.tradeengine.domain.scenario;

import org.springframework.stereotype.Component;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.event.PriceCandleEvent;
import pl.tradeengine.domain.model.*;
import pl.tradeengine.domain.port.DivergenceRepository;
import pl.tradeengine.domain.port.FvgRepository;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Component // Ważne, aby Spring go znalazł i dodał do ScenarioEngine!
public class KeyLevelDivergenceScenario implements Scenario {

    private final FvgRepository fvgRepository;
    private final DivergenceRepository divergenceRepository;

    public KeyLevelDivergenceScenario(FvgRepository fvgRepository, DivergenceRepository divergenceRepository) {
        this.fvgRepository = fvgRepository;
        this.divergenceRepository = divergenceRepository;
    }

    @Override
    public String name() {
        return "SIMPLE_FVG_DIVERGENCE_STRATEGY";
    }

    @Override
    public List<AlertToSend> onEvent(DomainEvent event) {
        // Ten scenariusz reaguje tylko na nowe ceny
        if (!(event instanceof PriceCandleEvent priceEvent)) {
            return List.of();
        }

        PriceCandle candle = priceEvent.candle();

        // 1. Sprawdź, czy cena weszła w jakiś otwarty FVG
        List<FvgZone> intersectedFvgs = fvgRepository.findIntersectingOpenFvgs(
                candle.symbol(), candle.timeframe(), candle.close() // Używamy ceny zamknięcia świecy
        );

        if (intersectedFvgs.isEmpty()) {
            return List.of(); // Nie jesteśmy w strefie FVG, koniec pracy
        }

        // 2. Jeśli tak, sprawdź czy była ostatnio dywergencja
        ZonedDateTime lookbackTime = ZonedDateTime.now().minus(Duration.ofHours(4)); // Patrzymy 4h wstecz
        Optional<DivergenceSignal> recentDivergenceOpt = divergenceRepository.findMostRecent(
                candle.symbol(), candle.timeframe(), lookbackTime
        );

        if (recentDivergenceOpt.isEmpty()) {
            return List.of(); // Nie było dywergencji, koniec pracy
        }

        FvgZone fvg = intersectedFvgs.get(0); // Bierzemy pierwszy znaleziony FVG
        DivergenceSignal divergence = recentDivergenceOpt.get();

        String description = String.format(
                "POTENCJAŁ: Cena %s weszła w FVG [%.2f-%.2f] przy istniejącej dywergencji %s!",
                candle.close(), fvg.getLowerPrice(), fvg.getUpperPrice(), divergence.getDirection()
        );

        AlertToSend alert = new AlertToSend(
                candle.symbol(),
                divergence.getDirection(),
                name(),
                candle.timeframe(),
                candle.close(),
                Optional.empty(),
                Optional.empty(),
                description
        );

        return List.of(alert);
    }

    @Override
    public Long id() {
        return 0L;
    }
}
