package pl.tradeengine.backtest;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.backtest.export.TradingViewExporter;
import pl.tradeengine.backtest.indicators.WaveTrendIndicator;
import pl.tradeengine.backtest.indicators.WtDivergenceDetector;
import pl.tradeengine.backtest.loader.HistoricalCandleLoader;
import pl.tradeengine.backtest.repository.InMemoryDivergenceRepository;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.scenario.DoubleDivergenceScenario;
import pl.tradeengine.domain.util.PriceCandleUtils;

import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Backtest of DOUBLE_DIVERGENCE_STRATEGY on one timeframe of one asset, no Spring.
 *
 * <p>Divergences are synthesised from CSV candles with {@link WtDivergenceDetector} (a port of
 * VuManChu Cipher B) and fed to the real {@link DoubleDivergenceScenario}, the way
 * {@code WebhookProcessingService.handleDivergence} does: save first, then emit the event.
 *
 * <p>Usage: {@code DoubleDivergenceBacktest [timeframe] [asset] [startDate]}; defaults H4, btc and
 * 2022-07-01. {@code asset} is the CSV prefix: {@code tao} reads {@code data/tao_h4.csv}.
 */
@Slf4j
public class DoubleDivergenceBacktest {

    public static void main(String[] args) throws Exception {
        Timeframe tf = args.length > 0 ? Timeframe.valueOf(args[0].toUpperCase(Locale.ROOT)) : Timeframe.H4;
        String asset = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "btc";
        ZonedDateTime startDate = ZonedDateTime.parse(args.length > 2 ? args[2] : "2022-07-01T00:00:00Z");

        Symbol symbol = new Symbol(asset.toUpperCase(Locale.ROOT) + "USDT");
        String file = "data/" + asset + "_" + tf.name().toLowerCase(Locale.ROOT) + ".csv";
        List<PriceCandle> candles = new HistoricalCandleLoader().loadFromCsv(Paths.get(file), symbol, tf);

        InMemoryDivergenceRepository divergenceRepo = new InMemoryDivergenceRepository();
        DoubleDivergenceScenario scenario = new DoubleDivergenceScenario(divergenceRepo);
        WaveTrendIndicator wt = new WaveTrendIndicator(9, 12, 3);
        WtDivergenceDetector detector = new WtDivergenceDetector();

        List<AlertToSend> alerts = new ArrayList<>();
        int divergences = 0;

        for (PriceCandle candle : candles) {
            double wt2 = wt.next(PriceCandleUtils.hlc3(candle)).wt2();
            Optional<Direction> div = detector.next(wt2, candle.high().doubleValue(), candle.low().doubleValue());
            if (div.isEmpty()) {
                continue;
            }
            divergences++;
            // Close of the confirming bar = the moment TradingView fires the alert.
            DivergenceSignal saved = divergenceRepo.save(
                    new DivergenceSignal(null, symbol, tf, div.get(), 1.0, candle.closeTime()));
            alerts.addAll(scenario.onEvent(new DivergenceDetectedEvent(saved)));
        }

        // Every asset gets its prefix, BTC included, so exports for different markets never share a name.
        String out = "output/" + asset + "_double_divergence_" + tf.name().toLowerCase(Locale.ROOT) + ".pine";
        new TradingViewExporter().export(alerts, Paths.get(out), startDate, null);

        List<AlertToSend> inRange = alerts.stream().filter(a -> !a.getTimestamp().isBefore(startDate)).toList();
        log.info("DOUBLE_DIVERGENCE {} {} | candles {} | divergences {} | alerts {} (since {}: {} — {} LONG / {} SHORT) | {}",
                symbol.code(), tf, candles.size(), divergences, alerts.size(), startDate.toLocalDate(), inRange.size(),
                inRange.stream().filter(a -> a.getDirection() == Direction.LONG).count(),
                inRange.stream().filter(a -> a.getDirection() == Direction.SHORT).count(),
                out);
    }
}
