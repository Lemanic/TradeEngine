package pl.tradeengine.backtest;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.backtest.export.TradeReportWriter;
import pl.tradeengine.backtest.export.TradesPineExporter;
import pl.tradeengine.backtest.loader.HistoricalCandleLoader;
import pl.tradeengine.backtest.trade.ExitReason;
import pl.tradeengine.backtest.trade.LongTradePlanner;
import pl.tradeengine.backtest.trade.SignalOutcome;
import pl.tradeengine.backtest.trade.SignalPlan;
import pl.tradeengine.backtest.trade.TargetSelection;
import pl.tradeengine.backtest.trade.Trade;
import pl.tradeengine.backtest.trade.TradeStatistics;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static pl.tradeengine.backtest.DoubleDivergenceTradeBacktest.ATR_PERIOD;
import static pl.tradeengine.backtest.DoubleDivergenceTradeBacktest.INITIAL_CAPITAL;
import static pl.tradeengine.backtest.DoubleDivergenceTradeBacktest.LEG_LOOKBACK;
import static pl.tradeengine.backtest.DoubleDivergenceTradeBacktest.MIN_REWARD_TO_RISK;
import static pl.tradeengine.backtest.DoubleDivergenceTradeBacktest.RISK_PER_TRADE;
import static pl.tradeengine.backtest.DoubleDivergenceTradeBacktest.SL_LOOKBACK;

/**
 * DOUBLE_DIVERGENCE_STRATEGY, long only, on BTC (TradingView CRYPTO:BTCUSD files {@code data/btc_*.csv})
 * across signal timeframes, TP timeframes, TP selection and break-even — every run with the entry of
 * {@link LimitEntryTradeBacktest} (limit on an M15/M5 FVG below the alert, else market).
 *
 * <p>Only alerts closing in [{@link #FROM}, {@link #TO}) are traded; older bars warm up the
 * indicators and positions opened in the window run to their exit on later data. Each run writes its
 * trades, signals, HTML and Pine to {@link #OUT}, and {@code summary.csv} collects one row per run.
 *
 * <p>{@code entry-rules} as the first argument compares where the limit rests instead
 * ({@link LimitEntryTradeBacktest.EntryRule}) for H1 signals with TP on H4/H1 — on these files and on
 * the full Binance history — and writes to {@link #ENTRY_RULES_OUT}.
 *
 * <p>{@code tao} runs the same grid on TAO ({@code data/tao_*.csv}) from {@link #TAO_FROM} to the end of
 * the data, once with the default entry and once with {@link LimitEntryTradeBacktest.EntryRule#BASE},
 * and writes to {@link #TAO_OUT}.
 */
@Slf4j
public class DoubleDivergenceMatrixBacktest {

    static final ZonedDateTime FROM = ZonedDateTime.parse("2022-11-01T00:00:00Z");
    static final ZonedDateTime TO = ZonedDateTime.parse("2025-11-01T00:00:00Z");
    static final Path OUT = Paths.get("output/btc_dd_2022-2025");
    static final Path ENTRY_RULES_OUT = Paths.get("output/btc_dd_entry-rules");
    static final ZonedDateTime TAO_FROM = ZonedDateTime.parse("2023-10-01T00:00:00Z");
    static final Path TAO_OUT = Paths.get("output/tao_dd_2023-2026");
    static final double BREAK_EVEN_AT_R = 1.0;

    /** Candle files and the window of alerts traded on them; a null bound leaves that side open. */
    record Dataset(String name, String filePrefix, Symbol symbol, ZonedDateTime from, ZonedDateTime to, String source) {
    }

    static final Dataset TRADINGVIEW = new Dataset("tv", "data/btc_", new Symbol("BTC"), FROM, TO, "CRYPTO:BTCUSD");
    static final Dataset BINANCE = new Dataset("binance", "data/binance/btcusdt_", new Symbol("BTCUSDT"), null, null,
            "Binance BTCUSDT");
    static final Dataset TAO = new Dataset("tao", "data/tao_", new Symbol("TAO"), TAO_FROM, null, "CRYPTO:TAOBUSD");

    record Config(Timeframe signal, List<Timeframe> targets, TargetSelection selection, double breakEvenAtR,
                  LimitEntryTradeBacktest.EntryRule rule) {

        Config(Timeframe signal, List<Timeframe> targets, TargetSelection selection, double breakEvenAtR) {
            this(signal, targets, selection, breakEvenAtR, LimitEntryTradeBacktest.EntryRule.NEAR_EDGE);
        }

        String name() {
            return (rule.equals(LimitEntryTradeBacktest.EntryRule.NEAR_EDGE) ? "" : rule.label() + "_")
                    + signal.name().toLowerCase(Locale.ROOT)
                    + "_tp-" + targets.stream().map(t -> t.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining("-"))
                    + "_" + selection.name().toLowerCase(Locale.ROOT)
                    + (breakEvenAtR > 0 ? String.format(Locale.ROOT, "_be%.1fr", breakEvenAtR) : "");
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("entry-rules")) {
            entryRules();
            return;
        }
        List<Timeframe> timeframes = List.of(Timeframe.M5, Timeframe.M15, Timeframe.M30, Timeframe.H1, Timeframe.H2,
                Timeframe.H4, Timeframe.D1);
        if (args.length > 0 && args[0].equals("tao")) {
            List<Config> configs = new ArrayList<>(grid(LimitEntryTradeBacktest.EntryRule.NEAR_EDGE));
            configs.addAll(grid(LimitEntryTradeBacktest.EntryRule.BASE));
            writeAll(TAO, load(TAO, timeframes), configs, TAO_OUT, "summary.csv");
            return;
        }
        writeAll(TRADINGVIEW, load(TRADINGVIEW, timeframes), grid(LimitEntryTradeBacktest.EntryRule.NEAR_EDGE), OUT,
                "summary.csv");
    }

    /** M15, M30 and H1 signals aiming at H4/H1, H1 and H2 at D1/H4 — each by TP selection, with BE and without. */
    private static List<Config> grid(LimitEntryTradeBacktest.EntryRule rule) {
        List<Config> configs = new ArrayList<>();
        for (double be : new double[]{0, BREAK_EVEN_AT_R}) {
            for (TargetSelection selection : TargetSelection.values()) {
                for (Timeframe signal : List.of(Timeframe.M15, Timeframe.M30, Timeframe.H1)) {
                    configs.add(new Config(signal, List.of(Timeframe.H4, Timeframe.H1), selection, be, rule));
                }
                for (Timeframe signal : List.of(Timeframe.H1, Timeframe.H2)) {
                    configs.add(new Config(signal, List.of(Timeframe.D1, Timeframe.H4), selection, be, rule));
                }
            }
        }
        return configs;
    }

    /** Every {@link LimitEntryTradeBacktest.EntryRule} × BE for H1 signals with TP on H4/H1, on both datasets. */
    private static void entryRules() throws IOException {
        List<LimitEntryTradeBacktest.EntryRule> rules = List.of(LimitEntryTradeBacktest.EntryRule.NEAR_EDGE,
                new LimitEntryTradeBacktest.EntryRule(true, false), new LimitEntryTradeBacktest.EntryRule(false, true),
                new LimitEntryTradeBacktest.EntryRule(true, true),
                new LimitEntryTradeBacktest.EntryRule(true, false, false, true),
                new LimitEntryTradeBacktest.EntryRule(true, true, false, true),
                new LimitEntryTradeBacktest.EntryRule(true, true, true, true));
        List<Config> configs = new ArrayList<>();
        for (TargetSelection selection : TargetSelection.values()) {
            for (double be : new double[]{0, BREAK_EVEN_AT_R}) {
                for (LimitEntryTradeBacktest.EntryRule rule : rules) {
                    configs.add(new Config(Timeframe.H1, List.of(Timeframe.H4, Timeframe.H1), selection, be, rule));
                }
            }
        }
        for (Dataset dataset : List.of(TRADINGVIEW, BINANCE)) {
            Map<Timeframe, List<PriceCandle>> candles = load(dataset,
                    List.of(Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4));
            writeAll(dataset, candles, configs, ENTRY_RULES_OUT.resolve(dataset.name()), "summary.csv");
        }
    }

    private static Map<Timeframe, List<PriceCandle>> load(Dataset dataset, List<Timeframe> timeframes)
            throws IOException {
        HistoricalCandleLoader loader = new HistoricalCandleLoader();
        Map<Timeframe, List<PriceCandle>> candles = new EnumMap<>(Timeframe.class);
        for (Timeframe tf : timeframes) {
            candles.put(tf, loader.loadFromCsv(
                    Paths.get(dataset.filePrefix() + tf.name().toLowerCase(Locale.ROOT) + ".csv"), dataset.symbol(), tf));
        }
        return candles;
    }

    private static void writeAll(Dataset dataset, Map<Timeframe, List<PriceCandle>> candles, List<Config> configs,
                                 Path out, String summaryFile) throws IOException {
        Files.createDirectories(out);
        try (PrintWriter summary = new PrintWriter(Files.newBufferedWriter(out.resolve(summaryFile)))) {
            summary.println("entry_rule,signal_tf,targets,selection,be_r,alerts,no_target,rr_too_low,position_open,"
                    + "unfilled,trades,tp,be,sl,tp_pct,be_pct,sl_pct,limit_fvg,limit_ifvg,limit_m15,limit_m5,market,"
                    + "sum_r,sum_r_without_best,best_r,profit_factor,return_pct,max_dd_pct,avg_r");
            for (Config config : configs) {
                summary.println(runOne(dataset, candles, config, out));
                summary.flush();
            }
        }
        log.info("Summary: {}", out.resolve(summaryFile));
    }

    private static String runOne(Dataset dataset, Map<Timeframe, List<PriceCandle>> candles, Config c, Path out)
            throws IOException {
        List<PriceCandle> htf = new ArrayList<>();
        c.targets().forEach(tf -> htf.addAll(candles.get(tf)));
        htf.sort(Comparator.comparing(PriceCandle::closeTime));
        LongTradePlanner planner = new LongTradePlanner(SL_LOOKBACK, LEG_LOOKBACK, MIN_REWARD_TO_RISK,
                c.targets(), 0, ATR_PERIOD, c.selection());
        DoubleDivergenceTradeBacktest.Result result = LimitEntryTradeBacktest.run(dataset.symbol(),
                candles.get(c.signal()), candles.get(Timeframe.M15), candles.get(Timeframe.M5), htf, planner, true,
                c.breakEvenAtR(), EnumSet.of(Direction.LONG), dataset.from(), dataset.to(), c.rule());

        List<Trade> trades = result.trades();
        List<SignalPlan> signals = result.signals();
        TradeStatistics stats = TradeStatistics.of(trades, INITIAL_CAPITAL);
        String selection = c.selection().name().toLowerCase(Locale.ROOT);
        String title = "DD " + c.signal() + " " + dataset.symbol().code() + " long · limit on M15/M5 " + c.rule().label()
                + " · TP " + selection + " FVG "
                + c.targets() + (c.breakEvenAtR() > 0 ? String.format(Locale.ROOT, " · BE at %.1fR", c.breakEvenAtR()) : "");
        String settings = String.format(Locale.ROOT,
                "%s · alerts %s → %s (positions run to their exit) · long · capital $%,.0f · risk %.1f%% · "
                        + "SL under the low of %d %s bars · leg = %d bars · TP = far edge of %s open bearish FVG in leg "
                        + "(%s first, then %s) · min R:R %.1f%s · entry: limit in the closest zone between SL and alert "
                        + "(M15, else M5, else market) — %s — cancelled after %dh or once TP trades · "
                        + "one position at a time · executed on M15 · no fees",
                dataset.source(), dataset.from() == null ? "start" : dataset.from().toLocalDate(),
                dataset.to() == null ? "end" : dataset.to().toLocalDate(), INITIAL_CAPITAL, RISK_PER_TRADE * 100, SL_LOOKBACK, c.signal(),
                LEG_LOOKBACK, selection, c.targets().get(0), c.targets().get(1), MIN_REWARD_TO_RISK,
                c.breakEvenAtR() > 0 ? String.format(Locale.ROOT, " · SL to entry after +%.1fR", c.breakEvenAtR()) : "",
                describe(c.rule()), LimitEntryTradeBacktest.LIMIT_TTL.toHours());

        Path base = out.resolve(c.name());
        TradeReportWriter report = new TradeReportWriter();
        report.writeTradesCsv(trades, Paths.get(base + ".csv"));
        report.writeSignalsCsv(signals, Paths.get(base + "_signals.csv"));
        report.writeHtml(title, settings, stats, trades, signals, Paths.get(base + ".html"));
        new TradesPineExporter().export(trades, signals, c.signal(), title, Paths.get(base + ".pine"));

        List<Trade> closed = trades.stream().filter(t -> t.exitReason() != ExitReason.OPEN_AT_END).toList();
        List<Double> r = closed.stream().map(Trade::rMultiple).sorted().toList();
        double sumR = r.stream().mapToDouble(Double::doubleValue).sum();
        double best = r.isEmpty() ? 0 : r.getLast();
        long tp = count(closed, ExitReason.TP), be = count(closed, ExitReason.BE), sl = count(closed, ExitReason.SL);
        int n = Math.max(closed.size(), 1);
        return String.format(Locale.ROOT, "%s,%s,%s,%s,%.1f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.1f,%.1f,%.1f,%d,%d,%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.3f",
                c.rule().label(), c.signal(), c.targets().stream().map(Enum::name).collect(Collectors.joining("-")), selection,
                c.breakEvenAtR(), signals.size(), outcomes(signals, SignalOutcome.NO_TARGET),
                outcomes(signals, SignalOutcome.RR_TOO_LOW), outcomes(signals, SignalOutcome.POSITION_OPEN),
                outcomes(signals, SignalOutcome.LIMIT_EXPIRED) + outcomes(signals, SignalOutcome.LIMIT_MISSED),
                closed.size(), tp, be, sl, 100.0 * tp / n, 100.0 * be / n, 100.0 * sl / n,
                entriesEndingWith(closed, "_FVG"), entriesEndingWith(closed, "_IFVG"),
                entriesStartingWith(closed, "LIMIT_M15"), entriesStartingWith(closed, "LIMIT_M5"),
                entriesStartingWith(closed, "MARKET"),
                sumR, sumR - best, best, stats.profitFactor(), stats.returnPct(), stats.maxDrawdownPct(), stats.avgR());
    }

    private static long count(List<Trade> trades, ExitReason reason) {
        return trades.stream().filter(t -> t.exitReason() == reason).count();
    }

    private static long entriesStartingWith(List<Trade> trades, String prefix) {
        return trades.stream().filter(t -> t.entryType().startsWith(prefix)).count();
    }

    private static long entriesEndingWith(List<Trade> trades, String suffix) {
        return trades.stream().filter(t -> t.entryType().endsWith(suffix)).count();
    }

    private static String describe(LimitEntryTradeBacktest.EntryRule rule) {
        return (rule.fvgAtExtreme() ? "bullish FVG at its far (lower) edge" : "bullish FVG at its near (upper) edge")
                + (rule.ifvg() ? ", inverted bearish FVG (IFVG) at its " + (rule.ifvgAtExtreme() ? "lower" : "upper")
                + " edge, the closer zone wins" : "")
                + (rule.deeperOfBoth() ? "; closest zone on M15 and on M5, limit at the lower of the two" : "");
    }

    private static long outcomes(List<SignalPlan> signals, SignalOutcome outcome) {
        return signals.stream().filter(s -> s.outcome() == outcome).count();
    }
}
