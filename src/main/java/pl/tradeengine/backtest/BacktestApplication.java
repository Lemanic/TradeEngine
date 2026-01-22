package pl.tradeengine.backtest;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.backtest.engine.BacktestRunner;
import pl.tradeengine.backtest.engine.BacktestScenarioEngine;  // ← DODAJ
import pl.tradeengine.backtest.export.TradingViewExporter;
import pl.tradeengine.backtest.indicators.WaveTrendIndicator;
import pl.tradeengine.backtest.loader.HistoricalCandleLoader;
import pl.tradeengine.backtest.registry.BacktestScenarioRegistry;  // ← DODAJ
import pl.tradeengine.backtest.repository.*;
import pl.tradeengine.domain.model.*;
import pl.tradeengine.domain.scenario.GrinderStrategyScenario;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
public class BacktestApplication {

    public static void main(String[] args) throws Exception {
        log.info("🚀 TradeEngine Backtest Starting...");
        testWaveTrendCross();
        // 1. Load historical candles
        Symbol btc = new Symbol("BTCUSDT");
        HistoricalCandleLoader loader = new HistoricalCandleLoader();

        Map<Timeframe, List<PriceCandle>> candlesByTf = new HashMap<>();
        candlesByTf.put(Timeframe.H1, loader.loadFromCsv(Paths.get("data/btc_h1.csv"), btc, Timeframe.H1));
        candlesByTf.put(Timeframe.H4, loader.loadFromCsv(Paths.get("data/btc_h4.csv"), btc, Timeframe.H4));
        candlesByTf.put(Timeframe.D1, loader.loadFromCsv(Paths.get("data/btc_d1.csv"), btc, Timeframe.D1));
        candlesByTf.put(Timeframe.W1, loader.loadFromCsv(Paths.get("data/btc_w1.csv"), btc, Timeframe.W1));

        // 2. Setup repositories
        InMemoryFvgRepository fvgRepo = new InMemoryFvgRepository();
        InMemoryBiasRepository biasRepo = new InMemoryBiasRepository();
        InMemorySwingPointRepository swingRepo = new InMemorySwingPointRepository();

        // 3. Setup strategies
        GrinderStrategyScenario swingStrategy = new GrinderStrategyScenario(
                fvgRepo, biasRepo, swingRepo,
                "GRINDER_SWING_D1_H1",
                Timeframe.D1,
                List.of(Timeframe.H4, Timeframe.D1),
                Timeframe.H1
        );

        // 4. Setup ScenarioRegistry & Engine
        BacktestScenarioRegistry scenarioRegistry = new BacktestScenarioRegistry();
        scenarioRegistry.register(swingStrategy);

        BacktestScenarioEngine scenarioEngine = new BacktestScenarioEngine(scenarioRegistry);

        // 5. Run backtest
        BacktestRunner runner = new BacktestRunner(candlesByTf, scenarioEngine, fvgRepo, biasRepo, swingRepo);
        List<AlertToSend> alerts = runner.run();

        // 6. Export results
        TradingViewExporter exporter = new TradingViewExporter();
        exporter.export(alerts, Paths.get("output/backtest_results.pine"));

        log.info("✅ Backtest completed. Check output/backtest_results.pine");
    }

    private static void testWaveTrendCross() {
        log.info("🧪 Testing WaveTrend indicator...");
        WaveTrendIndicator wt = new WaveTrendIndicator(9, 12, 3);

        int crossCount = 0;

        // Symuluj 100 świec z sinusoidą
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
        }
    }
}
