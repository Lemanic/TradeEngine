package pl.tradeengine.backtest;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.backtest.export.StructurePineExporter;
import pl.tradeengine.backtest.export.StructurePineExporter.LevelChange;
import pl.tradeengine.backtest.loader.HistoricalCandleLoader;
import pl.tradeengine.backtest.structure.MarketStructureTracker;
import pl.tradeengine.backtest.structure.StructureEvent;
import pl.tradeengine.backtest.structure.StructureEventType;
import pl.tradeengine.backtest.structure.Trend;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs {@link MarketStructureTracker} over one CSV and writes a Pine overlay of what it found, for
 * checking the structure rules on the chart.
 *
 * <p>Usage: {@code MarketStructureBacktest [asset] [timeframe] [from] [base|counter]}; defaults btc, H4,
 * 2024-01-01, base. {@code counter} also flips the trend on a completed counter-leg (see
 * {@link MarketStructureTracker}). Reads {@code data/{asset}_{timeframe}.csv} and writes
 * {@code output/{asset}_structure_{timeframe}.pine}, with a {@code _counter} suffix for that variant.
 * The whole file is replayed, so the structure is warmed up; only events from {@code from} on are
 * exported, to keep the script within Pine's limits.
 */
@Slf4j
public class MarketStructureBacktest {

    public static void main(String[] args) throws IOException {
        String asset = args.length > 0 ? args[0] : "btc";
        Timeframe timeframe = Timeframe.fromCode(args.length > 1 ? args[1].toUpperCase() : "H4");
        ZonedDateTime from = LocalDate.parse(args.length > 2 ? args[2] : "2024-01-01").atStartOfDay(ZoneOffset.UTC);
        boolean counterLegMss = args.length > 3 && args[3].equals("counter");
        String tf = timeframe.name().toLowerCase();
        String suffix = counterLegMss ? "_counter" : "";

        List<PriceCandle> candles = new HistoricalCandleLoader()
                .loadFromCsv(Paths.get("data/" + asset + "_" + tf + ".csv"), new Symbol(asset.toUpperCase()), timeframe);

        MarketStructureTracker tracker = new MarketStructureTracker(counterLegMss);
        List<StructureEvent> events = new ArrayList<>();
        List<LevelChange> levels = new ArrayList<>();
        LevelChange last = null;
        for (PriceCandle candle : candles) {
            List<StructureEvent> barEvents = tracker.onCandle(candle);
            if (candle.openTime().isBefore(from)) {
                continue;
            }
            events.addAll(barEvents);
            Trend trend = tracker.trend().orElse(null);
            if (last == null || last.level() != tracker.protectedLevel() || !Objects.equals(last.trend(), trend)) {
                last = new LevelChange(candle.openTime(), tracker.protectedLevel(), trend);
                levels.add(last);
            }
        }

        Map<StructureEventType, Integer> counts = new EnumMap<>(StructureEventType.class);
        events.forEach(e -> counts.merge(e.type(), 1, Integer::sum));
        log.info("{} {} from {}: {}, trend now {}, protected level {}",
                asset, timeframe, from.toLocalDate(), counts, tracker.trend().orElse(null), tracker.protectedLevel());

        String title = asset.toUpperCase() + " " + timeframe + " structure" + (counterLegMss ? " (counter-leg MSS)" : "");
        new StructurePineExporter().export(events, levels, title,
                Paths.get("output/" + asset + "_structure_" + tf + suffix + ".pine"));
    }
}
