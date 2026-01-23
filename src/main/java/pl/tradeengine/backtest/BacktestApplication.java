package pl.tradeengine.backtest;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.backtest.engine.BacktestRunner;
import pl.tradeengine.backtest.engine.BacktestScenarioEngine;
import pl.tradeengine.backtest.export.TradingViewExporter;
import pl.tradeengine.backtest.indicators.WaveTrendIndicator;
import pl.tradeengine.backtest.loader.HistoricalCandleLoader;
import pl.tradeengine.backtest.registry.BacktestScenarioRegistry;

import pl.tradeengine.backtest.repository.InMemoryBiasRepository;
import pl.tradeengine.backtest.repository.InMemoryFvgRepository;
import pl.tradeengine.backtest.repository.InMemorySwingPointRepository;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.scenario.GrinderStrategyScenario;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.ZonedDateTime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class BacktestApplication {

    public static void main(String[] args) throws Exception {
        log.info("🚀 TradeEngine Backtest Starting...");

        testWaveTrendCross();

        Symbol btc = new Symbol("BTCUSDT");
        HistoricalCandleLoader loader = new HistoricalCandleLoader();

        Map<Timeframe, List<PriceCandle>> candlesByTf = new HashMap<>();
        candlesByTf.put(Timeframe.H1, loader.loadFromCsv(Paths.get("data/btc_h1.csv"), btc, Timeframe.H1));
        candlesByTf.put(Timeframe.H4, loader.loadFromCsv(Paths.get("data/btc_h4.csv"), btc, Timeframe.H4));
        candlesByTf.put(Timeframe.D1, loader.loadFromCsv(Paths.get("data/btc_d1.csv"), btc, Timeframe.D1));
        candlesByTf.put(Timeframe.W1, loader.loadFromCsv(Paths.get("data/btc_w1.csv"), btc, Timeframe.W1));

        InMemoryFvgRepository fvgRepo = new InMemoryFvgRepository();
        InMemoryBiasRepository biasRepo = new InMemoryBiasRepository();
        InMemorySwingPointRepository swingRepo = new InMemorySwingPointRepository();

        // 3. Setup strategies

        // STRATEGY 1: Swing Trading (D1 bias + H1 trigger)
        GrinderStrategyScenario swingStrategy = new GrinderStrategyScenario(
                fvgRepo, biasRepo, swingRepo,
                "GRINDER_SWING_D1_H1",
                Timeframe.D1,           // Bias timeframe
                List.of(Timeframe.D1),  // FVG timeframes
                Timeframe.H1            // Trigger timeframe
        );

        // STRATEGY 2: Position Trading (W1 bias + H4 trigger)
        GrinderStrategyScenario positionStrategy = new GrinderStrategyScenario(
                fvgRepo, biasRepo, swingRepo,
                "GRINDER_POSITION_W1_H4",
                Timeframe.W1,
                List.of(Timeframe.D1, Timeframe.W1),
                Timeframe.H4
        );

        BacktestScenarioRegistry scenarioRegistry = new BacktestScenarioRegistry();
        scenarioRegistry.register(swingStrategy);
        scenarioRegistry.register(positionStrategy);

        BacktestScenarioEngine scenarioEngine = new BacktestScenarioEngine(scenarioRegistry);

        BacktestRunner runner = new BacktestRunner(candlesByTf, scenarioEngine, fvgRepo, biasRepo, swingRepo);
        List<AlertToSend> alerts = runner.run();

        List<AlertToSend> swingAlerts = alerts.stream()
                .filter(a -> a.getScenarioName().equals("GRINDER_SWING_D1_H1"))
                .collect(Collectors.toList());

        List<AlertToSend> positionAlerts = alerts.stream()
                .filter(a -> a.getScenarioName().equals("GRINDER_POSITION_W1_H4"))
                .collect(Collectors.toList());

        TradingViewExporter exporter = new TradingViewExporter();

        ZonedDateTime startDate = ZonedDateTime.parse("2022-07-01T00:00:00Z");
        ZonedDateTime endDate = null;

        exporter.export(alerts, Paths.get("output/backtest_all_strategies.pine"), startDate, endDate);
        exporter.export(swingAlerts, Paths.get("output/swing_strategy.pine"), startDate, endDate);
        exporter.export(positionAlerts, Paths.get("output/position_strategy.pine"), startDate, endDate);

        log.info("╔═══════════════════════════════════════════════╗");
        log.info("║         BACKTEST SUMMARY                      ║");
        log.info("╠═══════════════════════════════════════════════╣");
        log.info("║ Total Alerts:                          {} ║", String.format("%6d", alerts.size()));
        log.info("║                                               ║");
        log.info("║ SWING Strategy (D1/H1):                {} ║", String.format("%6d", swingAlerts.size()));
        log.info("║   - LONG:                              {} ║",
                String.format("%6d", swingAlerts.stream().filter(a -> a.getDirection() == Direction.LONG).count()));
        log.info("║   - SHORT:                             {} ║",
                String.format("%6d", swingAlerts.stream().filter(a -> a.getDirection() == Direction.SHORT).count()));
        log.info("║                                               ║");
        log.info("║ POSITION Strategy (W1/H4):             {} ║", String.format("%6d", positionAlerts.size()));
        log.info("║   - LONG:                              {} ║",
                String.format("%6d", positionAlerts.stream().filter(a -> a.getDirection() == Direction.LONG).count()));
        log.info("║   - SHORT:                             {} ║",
                String.format("%6d", positionAlerts.stream().filter(a -> a.getDirection() == Direction.SHORT).count()));
        log.info("╚═══════════════════════════════════════════════╝");

    }

    private static void testWaveTrendCross() {
        log.info("🧪 Testing WaveTrend indicator...");
        WaveTrendIndicator wt = new WaveTrendIndicator(9, 12, 3);

        int crossCount = 0;

        for (int i = 0; i < 100; i++) {
            double price = 50000 + 5000 * Math.sin(i * 0.2);
            WaveTrendIndicator.WaveTrendResult result = wt.next(BigDecimal.valueOf(price));

            if (result.cross()) {
                crossCount++;
                log.info("  ✓ Cross #{} at candle {}: wt1={}, wt2={}, crossUp={}",
                        crossCount, i, result.wt1(), result.wt2(), result.crossUp());
            }
        }

        log.info("🧪 Test complete: {} crosses detected in 100 candles", crossCount);

        if (crossCount == 0) {
            log.error("❌ WaveTrend indicator NEVER crosses! Check implementation.");
        } else {
            log.info("✅ WaveTrend works correctly");
        }
    }
}
