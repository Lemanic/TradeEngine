package pl.tradeengine.backtest.engine;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.backtest.indicators.FvgDetector;
import pl.tradeengine.backtest.indicators.WaveTrendIndicator;
import pl.tradeengine.backtest.loader.CandleTimeline;
import pl.tradeengine.backtest.repository.InMemoryBiasRepository;
import pl.tradeengine.backtest.repository.InMemoryFvgRepository;
import pl.tradeengine.backtest.repository.InMemorySwingPointRepository;

import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.event.FvgCreatedEvent;
import pl.tradeengine.domain.event.FvgFilledEvent;
import pl.tradeengine.domain.event.FvgTouchedEvent;
import pl.tradeengine.domain.event.SwingPointDetectedEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.BiasStatus;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.util.PriceCandleUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    public BacktestRunner(
            Map<Timeframe, List<PriceCandle>> candlesByTf,
            BacktestScenarioEngine scenarioEngine,
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

    }

    public List<AlertToSend> run() {
        while (timeline.hasNext()) {
            CandleTimeline.CandleClosedEvent event = timeline.getNextEvent();
            processCandle(event.timeframe(), event.candle());
        }
        return generatedAlerts;
    }

    private void processCandle(Timeframe tf, PriceCandle candle) {
        Symbol symbol = candle.symbol();

        WaveTrendIndicator wtIndicator = wtIndicators.get(tf);
        BigDecimal hlc3 = PriceCandleUtils.hlc3(candle);
        WaveTrendIndicator.WaveTrendResult wt = wtIndicator.next(hlc3);

        if (wt.cross()) {
            handleWaveTrendCross(symbol, tf, wt, candle);
        }

        FvgDetector fvgDetector = fvgDetectors.get(tf);
        Optional<FvgZone> fvg = fvgDetector.detect(candle);

        if (fvg.isPresent()) {
            handleFvgCreated(fvg.get());
        }

        updateFvgStates(candle);
    }

    private void handleWaveTrendCross(Symbol symbol, Timeframe tf,
                                      WaveTrendIndicator.WaveTrendResult wt,
                                      PriceCandle candle) {

        if (BIAS_TIMEFRAMES.contains(tf)) {
            BiasStatus bias = wt.crossUp() ? BiasStatus.BULLISH : BiasStatus.BEARISH;
            biasRepo.updateBias(symbol, tf, bias, "MOMENTUM_WAVE_" + tf.name());

        } else {
            String swingType = wt.crossUp() ? "SWING_LOW" : "SWING_HIGH";
            BigDecimal price = candle.close();

            swingRepo.save(symbol, tf, swingType, price, candle.closeTime());

            SwingPointDetectedEvent event = new SwingPointDetectedEvent(
                    symbol, tf, swingType, price, candle.closeTime()
            );

            processEvent(event);
        }
    }


    private void handleFvgCreated(FvgZone fvg) {
        FvgZone saved = fvgRepo.save(fvg);
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

            if (fvg.getDirection() == Direction.LONG) {
                isFilled = candle.low().compareTo(fvg.getLowerPrice()) < 0;

            } else if (fvg.getDirection() == Direction.SHORT) {
                isFilled = candle.high().compareTo(fvg.getUpperPrice()) > 0;
            }

            if (isFilled) {
                fvgRepo.markFilled(fvg.getId(), candle.closeTime(), null);
                FvgZone filledFvg = fvgRepo.findById(fvg.getId()).orElseThrow();
                FvgFilledEvent event = new FvgFilledEvent(filledFvg, candle.closeTime());
                processEvent(event);

            } else if (fvg.getStatus() == FvgStatus.CREATED) {
                boolean isTouched = false;

                if (fvg.getDirection() == Direction.LONG) {
                    isTouched = candle.low().compareTo(fvg.getLowerPrice()) > 0
                            && candle.low().compareTo(fvg.getUpperPrice()) < 0;
                } else {
                    isTouched = candle.high().compareTo(fvg.getLowerPrice()) > 0
                            && candle.high().compareTo(fvg.getUpperPrice()) < 0;
                }

                if (isTouched) {
                    fvgRepo.markTouched(fvg.getId(), candle.closeTime());
                    FvgZone touchedFvg = fvgRepo.findById(fvg.getId()).orElseThrow();
                    FvgTouchedEvent event = new FvgTouchedEvent(touchedFvg, candle.closeTime());
                    processEvent(event);
                }
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
