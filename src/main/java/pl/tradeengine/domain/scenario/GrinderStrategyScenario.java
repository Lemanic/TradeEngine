package pl.tradeengine.domain.scenario;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.event.FvgTouchedEvent;
import pl.tradeengine.domain.event.SwingPointDetectedEvent;
import pl.tradeengine.domain.event.FvgFilledEvent;

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

//        if (event instanceof FvgTouchedEvent fvgEvent) {
//            return handleFvgTouchTrigger(fvgEvent);
//        }
//
//        if (event instanceof FvgFilledEvent fvgEvent) {
//            return handleFvgInteraction(fvgEvent.fvgZone(), fvgEvent.filledAt());
//        }

        return List.of();
    }

    private List<AlertToSend> handleFvgInteraction(FvgZone fvg, ZonedDateTime interactionTime) {
        Symbol symbol = fvg.getSymbol();

//        log.info("🔍 handleFvgInteraction: FVG #{} filled at {}", fvg.getId(), interactionTime);

        if (!poiTimeframes.contains(fvg.getTimeframe())) {
//            log.debug("  ❌ Rejected: FVG timeframe not in POI list");
            return List.of();
        }

        BiasStatus currentBias = biasRepository.getBias(symbol, biasTimeframe);
        Direction tradeDirection = resolveDirectionFromBias(currentBias);

        if (tradeDirection != fvg.getDirection()) {
//            log.debug("  ❌ Rejected: direction mismatch");
            return List.of();
        }

        String expectedSwingType = (tradeDirection == Direction.LONG) ? "SWING_LOW" : "SWING_HIGH";

        ZonedDateTime lookbackTime = interactionTime
                .minus(triggerTimeframe.getDuration().multipliedBy(SWING_LOOKBACK_CANDLES));

//        log.info("  📅 Looking for {} since {}", expectedSwingType, lookbackTime);

        List<StoredSwingPoint> recentSwings = swingPointRepository.findRecentSwings(
                symbol,
                triggerTimeframe,
                expectedSwingType,
                lookbackTime
        );

        if (recentSwings.isEmpty()) {
//            log.debug("[{}] FVG Interaction (Touched/Filled) but NO recent swing found since {}.", strategyName, lookbackTime);
            return List.of();
        }

        StoredSwingPoint lastSwing = recentSwings.get(recentSwings.size() - 1);

//        log.info("🚀 [{}] Late FVG Entry detected! Interaction at {}, Swing found at price {}",
//                strategyName, interactionTime, lastSwing.price());

        return generateAlert(symbol, tradeDirection, fvg, lastSwing.price(), "Late FVG Entry (Pre-Swing)", interactionTime);
    }

    private List<AlertToSend> handleSwingTrigger(SwingPointDetectedEvent signal) {
        Symbol symbol = signal.symbol();
        Timeframe tf = signal.timeframe();


//        log.info("🔍 [{}] handleSwingTrigger: {} on {} type={} at {}",  // ← Dodaj nazwę strategii
//                strategyName, symbol.code(), tf, signal.type(), signal.detectedAt());

        if (tf != triggerTimeframe) {
//            log.debug("  [{}] ❌ Rejected: wrong timeframe (expected {}, got {})", strategyName, triggerTimeframe, tf);
            return List.of();
        }

        BiasStatus currentBias = biasRepository.getBias(symbol, biasTimeframe);
        Direction tradeDirection = resolveDirectionFromBias(currentBias);

//        log.debug("  [{}] Current bias: {} -> direction: {}", strategyName, currentBias, tradeDirection);

        if (tradeDirection == null) {
//            log.debug("  [{}] ❌ Rejected: no bias set", strategyName);
            return List.of();
        }

        String expectedSwingType = (tradeDirection == Direction.LONG) ? "SWING_LOW" : "SWING_HIGH";
        if (!signal.type().equals(expectedSwingType)) {
//            log.debug("  [{}] ❌ Rejected: swing type mismatch", strategyName);
            return List.of();
        }

        ZonedDateTime lookbackTime = signal.detectedAt().minus(
                triggerTimeframe.getDuration().multipliedBy(SWING_LOOKBACK_CANDLES)
        );

//        log.info("  [{}] 📅 Lookback window: from {} to {} ({} candles = {}h)",
//                strategyName, lookbackTime, signal.detectedAt(),
//                SWING_LOOKBACK_CANDLES,
//                triggerTimeframe.getDuration().multipliedBy(SWING_LOOKBACK_CANDLES).toHours());

        List<FvgZone> activeContextFvgs = fvgRepository.findActiveForSymbolAndDirectionOnHigherTf(
                symbol,
                tradeDirection,
                List.of(FvgStatus.TOUCHED, FvgStatus.FILLED),
                poiTimeframes
        );

//        log.info("  [{}] 📦 Found {} FVGs with status TOUCHED/FILLED (before TTL filter)",
//                strategyName, activeContextFvgs.size());

        // FILTRUJ PO TTL
        List<FvgZone> recentFvgs = activeContextFvgs.stream()
                .filter(fvg -> {
                    ZonedDateTime fvgEventTime = fvg.getTouchedAt() != null
                            ? fvg.getTouchedAt()
                            : fvg.getFilledAt();

                    if (fvgEventTime == null) {
                        if (fvg.getId() != null && fvg.getId() == 3973L) {
                            log.warn("  [{}] ⚠️ FVG #3973 has NO touched/filled timestamp!", strategyName);
                        }
                        return false;
                    }

                    boolean isRecent = fvgEventTime.isAfter(lookbackTime) || fvgEventTime.isEqual(lookbackTime);

                    long ageHours = java.time.Duration.between(fvgEventTime, signal.detectedAt()).toHours();
                    long maxHours = triggerTimeframe.getDuration().multipliedBy(SWING_LOOKBACK_CANDLES).toHours();

                    if (isRecent) {
//                        log.info("    [{}] ✅ FVG #{}: {} on {} | Event: {} | Age: {}h / max {}h | PASSED",
//                                strategyName, fvg.getId(), fvg.getDirection(), fvg.getTimeframe(),
//                                fvgEventTime, ageHours, maxHours);
                    }

                    return isRecent;
                })
                .collect(Collectors.toList());

//        log.info("  [{}] 📦 After TTL filter: {} recent FVGs", strategyName, recentFvgs.size());

        // ✅ FIX: Sprawdź recentFvgs (po filtrze)
        if (recentFvgs.isEmpty()) {
//            log.info("  [{}] ❌ No recent FVGs - returning empty", strategyName);
            return List.of();
        }

        // ✅ FIX: Użyj recentFvgs (po filtrze)
        FvgZone selectedFvg = recentFvgs.get(0);

//        log.info("  [{}] ✅ Generating alert using FVG #{}", strategyName, selectedFvg.getId());

        return generateAlert(symbol, tradeDirection, selectedFvg, signal.price(),
                "Swing Trigger", signal.detectedAt());
    }
//        if (activeContextFvgs.isEmpty()) return List.of();
//
//        return generateAlert(symbol, tradeDirection, activeContextFvgs.get(0), signal.price(), "Swing Trigger", signal.detectedAt());
//}

    private List<AlertToSend> handleFvgTouchTrigger(FvgTouchedEvent event) {
        FvgZone fvg = event.fvgZone();
        Symbol symbol = fvg.getSymbol();
//        log.info("🔍 handleFvgTouchTrigger: FVG #{} touched at {}", fvg.getId(), event.touchedAt());

        if (!poiTimeframes.contains(fvg.getTimeframe())) {
//            log.debug("  ❌ Rejected: FVG timeframe {} not in POI list", fvg.getTimeframe());
            return List.of();
        }

        BiasStatus currentBias = biasRepository.getBias(symbol, biasTimeframe);
        Direction tradeDirection = resolveDirectionFromBias(currentBias);

        if (tradeDirection != fvg.getDirection()){
//            log.debug("  ❌ Rejected: FVG direction {} doesn't match bias direction {}",
//                    fvg.getDirection(), tradeDirection);
            return List.of();
        }

        String expectedSwingType = (tradeDirection == Direction.LONG) ? "SWING_LOW" : "SWING_HIGH";

        ZonedDateTime lookbackTime = event.touchedAt()
                .minus(triggerTimeframe.getDuration().multipliedBy(SWING_LOOKBACK_CANDLES));
//        log.info("  📅 Looking for {} since {} (lookback {} candles)",
//                expectedSwingType, lookbackTime, SWING_LOOKBACK_CANDLES);

        List<StoredSwingPoint> recentSwings = swingPointRepository.findRecentSwings(
                symbol,
                triggerTimeframe,
                expectedSwingType,
                lookbackTime
        );

        if (recentSwings.isEmpty()) {
//            log.debug("[{}] FVG Touched but NO recent swing found.", strategyName);
            return List.of();
        }

        StoredSwingPoint lastSwing = recentSwings.get(recentSwings.size() - 1);

//        log.info("🚀 [{}] Late FVG Entry detected! Found Swing from {}", strategyName, lastSwing.price());

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

        // Timeline w jednej lub dwóch liniach
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
                symbol, dir, strategyName, triggerTimeframe, entryPrice, Optional.empty(), Optional.empty(), description, timestamp
        ));
    }

//    private List<AlertToSend> generateAlert(Symbol symbol, Direction dir, FvgZone fvg, java.math.BigDecimal entryPrice, String method, ZonedDateTime timestamp) {
//        ZonedDateTime fvgEventTime = fvg.getTouchedAt() != null ? fvg.getTouchedAt() : fvg.getFilledAt();
//        long timeDiffMinutes = fvgEventTime != null
//                ? java.time.Duration.between(fvgEventTime, timestamp).toMinutes()
//                : -1;
//
//        log.info("╔══════════════════════════════════════════════════════════════╗");
//        log.info("║  🚨 ALERT GENERATED - [{}]", strategyName);
//        log.info("╟──────────────────────────────────────────────────────────────╢");
//        log.info("║  Direction:      {} {}", dir, symbol.code());
//        log.info("║  Entry Price:    {}", PriceFormatter.format(entryPrice));
//        log.info("║  Alert Time:     {} ← SWING DETECTED", timestamp);  // ← Zmieniono
//        log.info("║  Method:         {}", method);
//        log.info("╟──────────────────────────────────────────────────────────────╢");
//        log.info("║  FVG Details:");
//        log.info("║    ID:           #{}", fvg.getId());
//        log.info("║    Timeframe:    {}", fvg.getTimeframe());
//        log.info("║    Status:       {}", fvg.getStatus());
//        log.info("║    Direction:    {}", fvg.getDirection());
//        log.info("║    Price Range:  {} - {}",
//                PriceFormatter.format(fvg.getLowerPrice()),
//                PriceFormatter.format(fvg.getUpperPrice()));
//
//        // ✅ Pokazuj touched i filled osobno
//        if (fvg.getTouchedAt() != null) {
//            long touchedMinutesAgo = java.time.Duration.between(fvg.getTouchedAt(), timestamp).toMinutes();
//            log.info("║    Touched At:   {} ({}min ago)", fvg.getTouchedAt(), touchedMinutesAgo);
//        } else {
//            log.info("║    Touched At:   N/A");
//        }
//
//        if (fvg.getFilledAt() != null) {
//            long filledMinutesAgo = java.time.Duration.between(fvg.getFilledAt(), timestamp).toMinutes();
//            log.info("║    Filled At:    {} ({}min ago)", fvg.getFilledAt(), filledMinutesAgo);
//        } else {
//            log.info("║    Filled At:    N/A");
//        }
//
//        // ✅ Timeline
//        log.info("║");
//        log.info("║  Timeline:");
//        if (fvg.getTouchedAt() != null) {
//            log.info("║    1️⃣  FVG Touched:  {}", fvg.getTouchedAt());
//        }
//        if (fvg.getFilledAt() != null) {
//            log.info("║    2️⃣  FVG Filled:   {}", fvg.getFilledAt());
//        }
//        log.info("║    3️⃣  Swing/Alert:  {} ← NOW", timestamp);
//
//        if (fvgEventTime != null) {
//            log.info("║");
//            log.info("║    ⏱️  FVG → Alert delay: {} minutes", timeDiffMinutes);
//        }
//
//        log.info("╚══════════════════════════════════════════════════════════════╝");
//
//        String description = String.format(
//                "🚀 GRINDER SETUP (%s)\n" +
//                        "Pair: %s\n" +
//                        "Strategy: %s\n" +
//                        "Bias: %s\n" +
//                        "Zone: %s FVG [%s - %s]\n" +
//                        "Entry: %s",
//                method,
//                symbol.code(),
//                strategyName,
//                biasTimeframe,
//                fvg.getTimeframe(),
//                PriceFormatter.format(fvg.getLowerPrice()),
//                PriceFormatter.format(fvg.getUpperPrice()),
//                PriceFormatter.format(entryPrice)
//        );
//
//        return List.of(new AlertToSend(
//                symbol, dir, strategyName, triggerTimeframe, entryPrice, Optional.empty(), Optional.empty(), description, timestamp
//        ));
//    }

}
