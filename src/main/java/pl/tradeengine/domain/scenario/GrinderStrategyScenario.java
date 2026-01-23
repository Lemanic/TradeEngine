package pl.tradeengine.domain.scenario;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.event.FvgFilledEvent;
import pl.tradeengine.domain.event.FvgTouchedEvent;
import pl.tradeengine.domain.event.SwingPointDetectedEvent;

import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.BiasStatus;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.StoredSwingPoint;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.BiasRepository;
import pl.tradeengine.domain.port.FvgRepository;
import pl.tradeengine.domain.port.SwingPointRepository;
import pl.tradeengine.domain.util.PriceFormatter;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

        // TURN IT OFF FOR BACKTESTING
        if (event instanceof FvgTouchedEvent fvgEvent) {
            return handleFvgTouchTrigger(fvgEvent);
        }

        if (event instanceof FvgFilledEvent fvgEvent) {
            return handleFvgInteraction(fvgEvent.fvgZone(), fvgEvent.filledAt());
        }

        return List.of();
    }

    private List<AlertToSend> handleFvgInteraction(FvgZone fvg, ZonedDateTime interactionTime) {
        Symbol symbol = fvg.getSymbol();

        if (!poiTimeframes.contains(fvg.getTimeframe())) {
            return List.of();
        }

        BiasStatus currentBias = biasRepository.getBias(symbol, biasTimeframe);
        Direction tradeDirection = resolveDirectionFromBias(currentBias);

        if (tradeDirection != fvg.getDirection()) {
            return List.of();
        }

        String expectedSwingType = (tradeDirection == Direction.LONG) ? "SWING_LOW" : "SWING_HIGH";

        ZonedDateTime lookbackTime = interactionTime
                .minus(triggerTimeframe.getDuration().multipliedBy(SWING_LOOKBACK_CANDLES));

        List<StoredSwingPoint> recentSwings = swingPointRepository.findRecentSwings(
                symbol,
                triggerTimeframe,
                expectedSwingType,
                lookbackTime
        );

        if (recentSwings.isEmpty()) {
            return List.of();
        }

        StoredSwingPoint lastSwing = recentSwings.get(recentSwings.size() - 1);

        return generateAlert(symbol, tradeDirection, fvg, lastSwing.price(), "Late FVG Entry (Pre-Swing)", interactionTime);
    }

    private List<AlertToSend> handleSwingTrigger(SwingPointDetectedEvent signal) {
        Symbol symbol = signal.symbol();
        Timeframe tf = signal.timeframe();

        if (tf != triggerTimeframe) {
            return List.of();
        }

        BiasStatus currentBias = biasRepository.getBias(symbol, biasTimeframe);
        Direction tradeDirection = resolveDirectionFromBias(currentBias);

        if (tradeDirection == null) {
            return List.of();
        }

        String expectedSwingType = (tradeDirection == Direction.LONG) ? "SWING_LOW" : "SWING_HIGH";
        if (!signal.type().equals(expectedSwingType)) {
            return List.of();
        }

        ZonedDateTime lookbackTime = signal.detectedAt().minus(
                triggerTimeframe.getDuration().multipliedBy(SWING_LOOKBACK_CANDLES)
        );

        List<FvgZone> activeContextFvgs = fvgRepository.findActiveForSymbolAndDirectionOnHigherTf(
                symbol,
                tradeDirection,
                List.of(FvgStatus.TOUCHED, FvgStatus.FILLED),
                poiTimeframes
        );

        List<FvgZone> recentFvgs = activeContextFvgs.stream()
                .filter(fvg -> {
                    ZonedDateTime fvgEventTime = fvg.getTouchedAt() != null
                            ? fvg.getTouchedAt()
                            : fvg.getFilledAt();

                    if (fvgEventTime == null) {
                        return false;
                    }

                    boolean isRecent = fvgEventTime.isAfter(lookbackTime) || fvgEventTime.isEqual(lookbackTime);

                    return isRecent;
                })
                .collect(Collectors.toList());

        if (recentFvgs.isEmpty()) {
            return List.of();
        }
        FvgZone selectedFvg = recentFvgs.get(0);

        return generateAlert(symbol, tradeDirection, selectedFvg, signal.price(),
                "Swing Trigger", signal.detectedAt());
    }

    private List<AlertToSend> handleFvgTouchTrigger(FvgTouchedEvent event) {
        FvgZone fvg = event.fvgZone();
        Symbol symbol = fvg.getSymbol();

        if (!poiTimeframes.contains(fvg.getTimeframe())) {
            return List.of();
        }

        BiasStatus currentBias = biasRepository.getBias(symbol, biasTimeframe);
        Direction tradeDirection = resolveDirectionFromBias(currentBias);

        if (tradeDirection != fvg.getDirection()){
            return List.of();
        }

        String expectedSwingType = (tradeDirection == Direction.LONG) ? "SWING_LOW" : "SWING_HIGH";

        ZonedDateTime lookbackTime = event.touchedAt()
                .minus(triggerTimeframe.getDuration().multipliedBy(SWING_LOOKBACK_CANDLES));

        List<StoredSwingPoint> recentSwings = swingPointRepository.findRecentSwings(
                symbol,
                triggerTimeframe,
                expectedSwingType,
                lookbackTime
        );

        if (recentSwings.isEmpty()) {
            return List.of();
        }

        StoredSwingPoint lastSwing = recentSwings.get(recentSwings.size() - 1);

        return generateAlert(symbol, tradeDirection, fvg, lastSwing.price(), "Late FVG Entry (Pre-Swing)", event.touchedAt());
    }

    private Direction resolveDirectionFromBias(BiasStatus bias) {
        if (bias == BiasStatus.BULLISH) return Direction.LONG;
        if (bias == BiasStatus.BEARISH) return Direction.SHORT;
        return null;
    }

    private List<AlertToSend> generateAlert(Symbol symbol, Direction dir, FvgZone fvg, java.math.BigDecimal entryPrice, String method, ZonedDateTime timestamp) {
        ZonedDateTime fvgEventTime = fvg.getTouchedAt() != null ? fvg.getTouchedAt() : fvg.getFilledAt();
        long timeDiffMinutes = fvgEventTime != null
                ? java.time.Duration.between(fvgEventTime, timestamp).toMinutes()
                : -1;

        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║  🚨 ALERT GENERATED - [{}] | {} {} @ {}",
                strategyName, dir, symbol.code(), PriceFormatter.format(entryPrice));
        log.info("║  Method: {} | Alert Time: {}", method, timestamp);
        log.info("╟──────────────────────────────────────────────────────────────╢");
        log.info("║  FVG #{} | {} | {} | {} | Range: {} - {}",
                fvg.getId(),
                fvg.getTimeframe(),
                fvg.getStatus(),
                fvg.getDirection(),
                PriceFormatter.format(fvg.getLowerPrice()),
                PriceFormatter.format(fvg.getUpperPrice()));

        StringBuilder timeline = new StringBuilder("║  Timeline: ");
        if (fvg.getTouchedAt() != null) {
            long touchedAgo = java.time.Duration.between(fvg.getTouchedAt(), timestamp).toMinutes();
            timeline.append(String.format("Touched %dmin ago", touchedAgo));
        }
        if (fvg.getFilledAt() != null) {
            long filledAgo = java.time.Duration.between(fvg.getFilledAt(), timestamp).toMinutes();
            if (fvg.getTouchedAt() != null) timeline.append(" → ");
            timeline.append(String.format("Filled %dmin ago", filledAgo));
        }
        timeline.append(" → Alert NOW");
        if (fvgEventTime != null) {
            timeline.append(String.format(" (⏱️ %dmin delay)", timeDiffMinutes));
        }
        log.info(timeline.toString());

        log.info("╚══════════════════════════════════════════════════════════════╝");

        String directionIcon = dir == Direction.LONG ? "🟢" : "🔴";

        String description = String.format(
                "🚀 GRINDER SETUP (%s)\n" +
                        "Pair: %s\n" +
                        "Direction: %s %s\n" +
                        "Strategy: %s\n" +
                        "Bias: %s\n" +
                        "Zone: %s FVG [%s - %s]",
                method,
                symbol.code(),
                directionIcon,
                dir,
                strategyName,
                biasTimeframe,
                fvg.getTimeframe(),
                PriceFormatter.format(fvg.getLowerPrice()),
                PriceFormatter.format(fvg.getUpperPrice())
        );

        return List.of(new AlertToSend(
                symbol, dir, strategyName, triggerTimeframe, entryPrice, Optional.empty(), Optional.empty(), description, timestamp
        ));
    }

}
