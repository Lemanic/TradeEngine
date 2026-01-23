package pl.tradeengine.backtest.engine;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.backtest.indicators.FvgDetector;
import pl.tradeengine.backtest.indicators.WaveTrendIndicator;
import pl.tradeengine.backtest.loader.CandleTimeline;
import pl.tradeengine.backtest.repository.*;
import pl.tradeengine.domain.event.*;
import pl.tradeengine.domain.model.*;
import pl.tradeengine.domain.scenario.ScenarioEngine;
import pl.tradeengine.domain.util.PriceCandleUtils;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
public class BacktestRunner {

    private final CandleTimeline timeline;
    private final BacktestScenarioEngine scenarioEngine;

    private final Map<Timeframe, WaveTrendIndicator> wtIndicators = new HashMap<>();
    private final Map<Timeframe, FvgDetector> fvgDetectors = new HashMap<>();

    private final InMemoryFvgRepository fvgRepo;
    private final InMemoryBiasRepository biasRepo;
    private final InMemorySwingPointRepository swingRepo;

    private final List<AlertToSend> generatedAlerts = new ArrayList<>();

    private static final Set<Timeframe> BIAS_TIMEFRAMES = Set.of(Timeframe.D1, Timeframe.W1);

    private int fvgCount = 0;
    private int swingCount = 0;
    private int biasChangeCount = 0;


    public BacktestRunner(
            Map<Timeframe, List<PriceCandle>> candlesByTf,
            BacktestScenarioEngine scenarioEngine,  // ← ZMIEŃ TYP (było: ScenarioEngine)
            InMemoryFvgRepository fvgRepo,
            InMemoryBiasRepository biasRepo,
            InMemorySwingPointRepository swingRepo
    ) {
        this.timeline = new CandleTimeline(candlesByTf);
        this.scenarioEngine = scenarioEngine;
        this.fvgRepo = fvgRepo;
        this.biasRepo = biasRepo;
        this.swingRepo = swingRepo;

        for (Timeframe tf : candlesByTf.keySet()) {
            wtIndicators.put(tf, new WaveTrendIndicator(9, 12, 3));
            fvgDetectors.put(tf, new FvgDetector());
        }

        log.info("BacktestRunner initialized with {} timeframes", candlesByTf.size());
    }

    public List<AlertToSend> run() {
        log.info("🚀 Starting backtest...");

        long startTime = System.currentTimeMillis();
        int processedCandles = 0;

        while (timeline.hasNext()) {
            CandleTimeline.CandleClosedEvent event = timeline.getNextEvent();
            processCandle(event.timeframe(), event.candle());
            processedCandles++;

            if (processedCandles % 1000 == 0) {
                log.info("Processed {} candles, generated {} alerts so far",
                        processedCandles, generatedAlerts.size());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("📊 STATS: {} FVGs detected, {} Swings, {} Bias changes",   // ← DODAJ
                fvgCount, swingCount, biasChangeCount);
        log.info("✅ Backtest completed in {}ms. Processed {} candles, generated {} alerts",
                duration, processedCandles, generatedAlerts.size());

        return generatedAlerts;
    }

    private void processCandle(Timeframe tf, PriceCandle candle) {
        Symbol symbol = candle.symbol();

        // 1. Calculate WaveTrend
        WaveTrendIndicator wtIndicator = wtIndicators.get(tf);
        BigDecimal hlc3 = PriceCandleUtils.hlc3(candle);
        WaveTrendIndicator.WaveTrendResult wt = wtIndicator.next(hlc3);

        // DEBUG: Log co 1000 świec dla D1
        if (tf == Timeframe.D1 && candle.openTime().getDayOfMonth() == 1) {  // ← DODAJ
            log.debug("WT D1 at {}: wt1={}, wt2={}, cross={}, crossUp={}",
                    candle.openTime(), wt.wt1(), wt.wt2(), wt.cross(), wt.crossUp());
        }

        // 2. Handle WaveTrend Cross
        if (wt.cross()) {
            handleWaveTrendCross(symbol, tf, wt, candle);
        }

        // 3. Detect FVG
        FvgDetector fvgDetector = fvgDetectors.get(tf);
        Optional<FvgZone> fvg = fvgDetector.detect(candle);

        if (fvg.isPresent()) {
            handleFvgCreated(fvg.get());
        }

        // 4. Update existing FVG states
        updateFvgStates(candle);
    }
    private void handleWaveTrendCross(Symbol symbol, Timeframe tf,
                                      WaveTrendIndicator.WaveTrendResult wt,
                                      PriceCandle candle) {

        if (BIAS_TIMEFRAMES.contains(tf)) {
            biasChangeCount++;
            BiasStatus bias = wt.crossUp() ? BiasStatus.BULLISH : BiasStatus.BEARISH;
            biasRepo.updateBias(symbol, tf, bias, "MOMENTUM_WAVE_" + tf.name());
            log.info("📢 BIAS UPDATE: {} on {} -> {} at {}", symbol.code(), tf, bias, candle.closeTime());  // ← DODAJ timestamp
        } else {
            swingCount++;
            String swingType = wt.crossUp() ? "SWING_LOW" : "SWING_HIGH";
            BigDecimal price = candle.close();

            swingRepo.save(symbol, tf, swingType, price, candle.closeTime());

            log.info("🌊 SWING DETECTED: {} on {} -> {} at price {} ({})",   // ← DODAJ logowanie
                    symbol.code(), tf, swingType, price, candle.closeTime());

            SwingPointDetectedEvent event = new SwingPointDetectedEvent(
                    symbol, tf, swingType, price, candle.closeTime()
            );

            processEvent(event);
        }
    }


    private void handleFvgCreated(FvgZone fvg) {
        fvgCount++;
        FvgZone saved = fvgRepo.save(fvg);

//        log.info("📦 FVG CREATED: {} {} on {} [{} - {}] at {}",   // ← DODAJ
//                saved.getSymbol().code(), saved.getDirection(), saved.getTimeframe(),
//                saved.getLowerPrice(), saved.getUpperPrice(),
//                "timestamp_placeholder");  // Możesz dodać timestamp do FvgZone jeśli chcesz

        FvgCreatedEvent event = new FvgCreatedEvent(saved);
        processEvent(event);
    }


    private void updateFvgStates(PriceCandle candle) {
        List<FvgZone> intersectedFvgs = fvgRepo.findIntersectingOpenFvgs(
                candle.symbol(), candle.low(), candle.high()
        );

        for (FvgZone fvg : intersectedFvgs) {
            if (fvg.getStatus() == FvgStatus.FILLED || fvg.getStatus() == FvgStatus.CONSUMED) {
                continue;
            }

            boolean isFilled = false;

            if (fvg.getDirection() == Direction.LONG && candle.low().compareTo(fvg.getLowerPrice()) <= 0) {
                isFilled = true;
            } else if (fvg.getDirection() == Direction.SHORT && candle.high().compareTo(fvg.getUpperPrice()) >= 0) {
                isFilled = true;
            }

            if (isFilled) {
                log.debug("🎯 Marking FVG #{} as FILLED at {}", fvg.getId(), candle.closeTime());  // ← DODAJ
                fvgRepo.markFilled(fvg.getId(), candle.closeTime(), null);

                FvgZone filledFvg = fvgRepo.findById(fvg.getId()).orElseThrow();
                FvgFilledEvent event = new FvgFilledEvent(filledFvg, candle.closeTime());
                processEvent(event);

            } else if (fvg.getStatus() == FvgStatus.CREATED) {
                log.debug("👉 Marking FVG #{} as TOUCHED at {}", fvg.getId(), candle.closeTime());  // ← DODAJ
                fvgRepo.markTouched(fvg.getId(), candle.closeTime());

                FvgZone touchedFvg = fvgRepo.findById(fvg.getId()).orElseThrow();
                FvgTouchedEvent event = new FvgTouchedEvent(touchedFvg, candle.closeTime());
                processEvent(event);
            }
        }
    }

    private void processEvent(DomainEvent event) {
        List<AlertToSend> alerts = scenarioEngine.onEvent(event);
        generatedAlerts.addAll(alerts);

        if (!alerts.isEmpty()) {
            for (AlertToSend alert : alerts) {
                log.info("🎯 ALERT: {} {} on {} at price {}",
                        alert.getDirection(), alert.getScenarioName(), alert.getTimeframe(), alert.getEntryPrice());
            }
        }
    }
}
