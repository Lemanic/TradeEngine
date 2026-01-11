package pl.tradeengine.domain.scenario;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.application.dto.SwingPointDto;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.event.FvgTouchedEvent;
import pl.tradeengine.domain.event.SwingPointDetectedEvent;
import pl.tradeengine.domain.model.*;
import pl.tradeengine.domain.port.BiasRepository;
import pl.tradeengine.domain.port.FvgRepository;
import pl.tradeengine.domain.port.SwingPointRepository;
import pl.tradeengine.domain.util.PriceFormatter;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
public class GrinderStrategyScenario implements Scenario {

    private final FvgRepository fvgRepository;
    private final BiasRepository biasRepository;
    private final SwingPointRepository swingPointRepository;

    private final String strategyName;
    private final Timeframe biasTimeframe;
    private final List<Timeframe> poiTimeframes;
    private final Timeframe triggerTimeframe;

    private static final int SWING_LOOKBACK_CANDLES = 5;

    public GrinderStrategyScenario(FvgRepository fvgRepository, BiasRepository biasRepository,
                                   SwingPointRepository swingPointRepository, String strategyName,
                                   Timeframe biasTimeframe, List<Timeframe> poiTimeframes, Timeframe triggerTimeframe) {
        this.fvgRepository = fvgRepository;
        this.biasRepository = biasRepository;
        this.swingPointRepository = swingPointRepository;
        this.strategyName = strategyName;
        this.biasTimeframe = biasTimeframe;
        this.poiTimeframes = poiTimeframes;
        this.triggerTimeframe = triggerTimeframe;
    }

    @Override
    public String name() {
        return strategyName;
    }

    @Override
    public Long id() {
        return (long) strategyName.hashCode();
    }

    @Override
    public List<AlertToSend> onEvent(DomainEvent event) {
        if (event instanceof SwingPointDetectedEvent swingEvent) {
            return handleSwingTrigger(swingEvent);
        }

        if (event instanceof FvgTouchedEvent fvgEvent) {
            return handleFvgTouchTrigger(fvgEvent);
        }

        return List.of();
    }

    private List<AlertToSend> handleSwingTrigger(SwingPointDetectedEvent signal) {
        Symbol symbol = signal.symbol();
        Timeframe tf = signal.timeframe();

        if (tf != triggerTimeframe) return List.of();

        BiasStatus currentBias = biasRepository.getBias(symbol, biasTimeframe);
        Direction tradeDirection = resolveDirectionFromBias(currentBias);
        if (tradeDirection == null) return List.of();

        String expectedSwingType = (tradeDirection == Direction.LONG) ? "SWING_LOW" : "SWING_HIGH";
        if (!signal.type().equals(expectedSwingType)) return List.of();

        List<FvgZone> activeContextFvgs = fvgRepository.findActiveForSymbolAndDirectionOnHigherTf(
                symbol,
                tradeDirection,
                List.of(FvgStatus.TOUCHED, FvgStatus.FILLED),
                poiTimeframes
        );

        if (activeContextFvgs.isEmpty()) return List.of();

        return generateAlert(symbol, tradeDirection, activeContextFvgs.get(0), signal.price(), "Swing Trigger");
    }

    private List<AlertToSend> handleFvgTouchTrigger(FvgTouchedEvent event) {
        FvgZone fvg = event.fvgZone();
        Symbol symbol = fvg.getSymbol();

        if (!poiTimeframes.contains(fvg.getTimeframe())) return List.of();

        BiasStatus currentBias = biasRepository.getBias(symbol, biasTimeframe);
        Direction tradeDirection = resolveDirectionFromBias(currentBias);

        if (tradeDirection != fvg.getDirection()) return List.of();

        String expectedSwingType = (tradeDirection == Direction.LONG) ? "SWING_LOW" : "SWING_HIGH";

        ZonedDateTime lookbackTime = event.touchedAt()
                .minus(triggerTimeframe.getDuration().multipliedBy(SWING_LOOKBACK_CANDLES));

        List<SwingPointDto> recentSwings = swingPointRepository.findRecentSwings(
                symbol,
                triggerTimeframe,
                expectedSwingType,
                lookbackTime
        );

        if (recentSwings.isEmpty()) {
            log.debug("[{}] FVG Touched but NO recent swing found.", strategyName);
            return List.of();
        }

        SwingPointDto lastSwing = recentSwings.get(recentSwings.size() - 1);

        log.info("🚀 [{}] Late FVG Entry detected! Found Swing from {}", strategyName, lastSwing.price());

        return generateAlert(symbol, tradeDirection, fvg, lastSwing.price(), "Late FVG Entry (Pre-Swing)");
    }

    private Direction resolveDirectionFromBias(BiasStatus bias) {
        if (bias == BiasStatus.BULLISH) return Direction.LONG;
        if (bias == BiasStatus.BEARISH) return Direction.SHORT;
        return null;
    }

    private List<AlertToSend> generateAlert(Symbol symbol, Direction dir, FvgZone fvg, java.math.BigDecimal entryPrice, String method) {
        String description = String.format(
                "🚀 GRINDER SETUP (%s)\n" +
                        "Pair: %s\n" +
                        "Strategy: %s\n" +
                        "Bias: %s\n" +
                        "Zone: %s FVG [%s - %s]\n" +
                        "Entry: %s",
                method,
                symbol.code(),
                strategyName,
                biasTimeframe,
                fvg.getTimeframe(),
                PriceFormatter.format(fvg.getLowerPrice()),
                PriceFormatter.format(fvg.getUpperPrice()),
                PriceFormatter.format(entryPrice)
        );

        return List.of(new AlertToSend(
                symbol, dir, strategyName, triggerTimeframe, entryPrice, Optional.empty(), Optional.empty(), description
        ));
    }

}
