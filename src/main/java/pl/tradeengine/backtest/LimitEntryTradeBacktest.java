package pl.tradeengine.backtest;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.backtest.export.TradeReportWriter;
import pl.tradeengine.backtest.export.TradesPineExporter;
import pl.tradeengine.backtest.indicators.WaveTrendIndicator;
import pl.tradeengine.backtest.indicators.WtDivergenceDetector;
import pl.tradeengine.backtest.loader.HistoricalCandleLoader;
import pl.tradeengine.backtest.repository.InMemoryDivergenceRepository;
import pl.tradeengine.backtest.structure.MarketStructureTracker;
import pl.tradeengine.backtest.structure.StructureEventType;
import pl.tradeengine.backtest.structure.Trend;
import pl.tradeengine.backtest.structure.TrendTimeline;
import pl.tradeengine.backtest.trade.BullishFvgTracker;
import pl.tradeengine.backtest.trade.HtfFvgTracker;
import pl.tradeengine.backtest.trade.InversionFvgTracker;
import pl.tradeengine.backtest.trade.LongTradePlanner;
import pl.tradeengine.backtest.trade.OpenFvg;
import pl.tradeengine.backtest.trade.OpenPosition;
import pl.tradeengine.backtest.trade.PriceMirror;
import pl.tradeengine.backtest.trade.SignalOutcome;
import pl.tradeengine.backtest.trade.SignalPlan;
import pl.tradeengine.backtest.trade.TargetSelection;
import pl.tradeengine.backtest.trade.Trade;
import pl.tradeengine.backtest.trade.TradeStatistics;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.scenario.DoubleDivergenceScenario;
import pl.tradeengine.domain.util.PriceCandleUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static pl.tradeengine.backtest.DoubleDivergenceTradeBacktest.ATR_PERIOD;
import static pl.tradeengine.backtest.DoubleDivergenceTradeBacktest.DEFAULT_TARGET_TIMEFRAMES;
import static pl.tradeengine.backtest.DoubleDivergenceTradeBacktest.INITIAL_CAPITAL;
import static pl.tradeengine.backtest.DoubleDivergenceTradeBacktest.LEG_LOOKBACK;
import static pl.tradeengine.backtest.DoubleDivergenceTradeBacktest.MIN_REWARD_TO_RISK;
import static pl.tradeengine.backtest.DoubleDivergenceTradeBacktest.RISK_PER_TRADE;
import static pl.tradeengine.backtest.DoubleDivergenceTradeBacktest.SL_LOOKBACK;

/**
 * DOUBLE_DIVERGENCE_STRATEGY on H1 with the same signal, SL and TP as
 * {@link DoubleDivergenceTradeBacktest}, but a different entry: after the alert a limit order rests on
 * the far edge of an open FVG lying between the swing extreme (SL) and the alert's close — M15 first,
 * M5 when M15 has none, a market entry at the next open when neither has one.
 *
 * <p>Longs and shorts can be traded alone or together; together they share one balance and one
 * position slot. A short is planned and simulated as a long on mirrored prices ({@link PriceMirror}),
 * so both sides run through the same planner, trackers and position code.
 *
 * <p>Everything after the signal runs on M15 bars, including the market entries, so a {@code market}
 * run of this class is the like-for-like baseline for a {@code limit} run. A limit order is cancelled
 * after {@link #LIMIT_TTL}, or as soon as price reaches TP before filling.
 *
 * <p>Usage: {@code LimitEntryTradeBacktest [market|limit] [breakEvenAtR] [highest|nearest] [minStopAtr]
 * [long|short|both] [asset] [d1] [mss] [from=YYYY-MM-DD] [to=YYYY-MM-DD]}; defaults limit, 0, highest, 0,
 * long, btcusdt, no bias, no MSS, all data.
 * {@code d1} acts on an alert only when the D1 market structure ({@link MarketStructureTracker}, base
 * rules) points its way — up for a long, down for a short — as of the last D1 candle closed before the
 * alert; reads {@code d1.csv} next to the other files. {@code mss} holds the entry until M15 structure
 * (counter-leg rules) shifts the alert's way, then rests the limit on an M15/M5 FVG below that bar's
 * close, with SL at the M15 protected low — see the {@code run} overload taking {@code afterMss}.
 * {@code from}/{@code to} act only on alerts closing in {@code [from, to)} (UTC midnights); earlier data
 * still warms up the indicators and structure. The flags combine, in any order. Reads
 * {@code data/binance/btcusdt_{m5,m15,h1,h4}.csv} for btcusdt and {@code data/{asset}_{m5,m15,h1,h4}.csv}
 * otherwise; M5 is optional (without it a missing M15 zone means a market entry). Alerts are acted on
 * only once M15 history exists — earlier H1 bars just warm up the indicators and the FVG targets.
 */
@Slf4j
public class LimitEntryTradeBacktest {

    static final Duration LIMIT_TTL = Duration.ofHours(24);
    static final Duration ENTRY_FVG_MAX_AGE = Duration.ofDays(7);

    public static void main(String[] args) throws IOException {
        boolean limit = args.length == 0 || args[0].equalsIgnoreCase("limit");
        double breakEvenAtR = args.length > 1 ? Double.parseDouble(args[1]) : 0;
        TargetSelection selection = args.length > 2
                ? TargetSelection.valueOf(args[2].toUpperCase(Locale.ROOT)) : TargetSelection.HIGHEST;
        double minStopAtr = args.length > 3 ? Double.parseDouble(args[3]) : 0;
        String sideArg = args.length > 4 ? args[4].toLowerCase(Locale.ROOT) : "long";
        Set<Direction> sides = switch (sideArg) {
            case "long" -> EnumSet.of(Direction.LONG);
            case "short" -> EnumSet.of(Direction.SHORT);
            case "both" -> EnumSet.allOf(Direction.class);
            default -> throw new IllegalArgumentException("side must be long, short or both: " + sideArg);
        };

        String asset = args.length > 5 ? args[5].toLowerCase(Locale.ROOT) : "btcusdt";
        String data = asset.equals("btcusdt") ? "data/binance/btcusdt_" : "data/" + asset + "_";
        Symbol symbol = new Symbol(asset.toUpperCase(Locale.ROOT));
        HistoricalCandleLoader loader = new HistoricalCandleLoader();
        List<PriceCandle> h1 = loader.loadFromCsv(Paths.get(data + "h1.csv"), symbol, Timeframe.H1);
        List<PriceCandle> m15 = loader.loadFromCsv(Paths.get(data + "m15.csv"), symbol, Timeframe.M15);
        boolean hasM5 = Files.exists(Paths.get(data + "m5.csv"));
        List<PriceCandle> m5 = limit && hasM5
                ? loader.loadFromCsv(Paths.get(data + "m5.csv"), symbol, Timeframe.M5) : List.of();
        List<PriceCandle> htf = new ArrayList<>(h1);
        htf.addAll(loader.loadFromCsv(Paths.get(data + "h4.csv"), symbol, Timeframe.H4));
        htf.sort(Comparator.comparing(PriceCandle::closeTime));
        List<String> flags = args.length > 6
                ? Stream.of(args).skip(6).map(a -> a.toLowerCase(Locale.ROOT)).toList() : List.of();
        boolean d1Bias = flags.contains("d1");
        boolean afterMss = flags.contains("mss");
        ZonedDateTime tradeFrom = dateFlag(flags, "from=");
        ZonedDateTime tradeTo = dateFlag(flags, "to=");
        if (afterMss && !limit) {
            throw new IllegalArgumentException("mss places a limit after the MSS, so it needs the limit mode");
        }
        BiPredicate<Direction, ZonedDateTime> bias = NO_BIAS;
        if (d1Bias) {
            TrendTimeline d1 = new TrendTimeline(
                    loader.loadFromCsv(Paths.get(data + "d1.csv"), symbol, Timeframe.D1), new MarketStructureTracker());
            bias = (side, time) -> d1.at(time).equals(Optional.of(side == Direction.LONG ? Trend.UP : Trend.DOWN));
        }

        LongTradePlanner planner = new LongTradePlanner(SL_LOOKBACK, LEG_LOOKBACK, MIN_REWARD_TO_RISK,
                DEFAULT_TARGET_TIMEFRAMES, minStopAtr, ATR_PERIOD, selection);
        DoubleDivergenceTradeBacktest.Result result = run(symbol, h1, m15, m5, htf, planner, limit, breakEvenAtR, sides,
                tradeFrom, tradeTo, EntryRule.NEAR_EDGE, bias, afterMss);
        TradeStatistics stats = TradeStatistics.of(result.trades(), INITIAL_CAPITAL);

        // Long-only keeps the file names it had before shorts existed.
        String sideSuffix = sideArg.equals("long") ? "" : "_" + sideArg;
        String variant = (limit ? "limit" : "market")
                + (minStopAtr > 0 ? String.format(Locale.ROOT, "_minsl%.1fatr", minStopAtr) : "")
                + (breakEvenAtR > 0 ? String.format(Locale.ROOT, "_be%.1fr", breakEvenAtR) : "")
                + (selection == TargetSelection.HIGHEST ? "" : "_" + selection.name().toLowerCase(Locale.ROOT))
                + sideSuffix
                + (d1Bias ? "_d1bias" : "")
                + (afterMss ? "_m15mss" : "")
                + (tradeFrom != null || tradeTo != null
                        ? "_" + (tradeFrom == null ? "start" : tradeFrom.toLocalDate()) + "_" + (tradeTo == null ? "end" : tradeTo.toLocalDate())
                        : "");
        String base = "output/" + asset + "_dd_h1_" + variant;
        String entryTfs = hasM5 ? "M15/M5" : "M15";
        String title = "DD H1 " + symbol.code() + " " + sideArg + " · " + (limit ? "limit on " + entryTfs + " FVG" : "market entry")
                + " · TP " + selection.name().toLowerCase(Locale.ROOT) + " FVG [H4, H1]"
                + (breakEvenAtR > 0 ? String.format(Locale.ROOT, " · BE at %.1fR", breakEvenAtR) : "")
                + (d1Bias ? " · only with D1 structure" : "")
                + (afterMss ? " · entry after M15 MSS" : "");
        String settings = String.format(Locale.ROOT,
                "%s %s → %s · %s%s · capital $%,.0f · risk %.1f%% · SL beyond the extreme of %d H1 bars%s · "
                        + "leg = %d bars · TP = far edge of %s open FVG in leg (H4, then H1) · min R:R %.1f "
                        + "(from alert close)%s · %s · one position at a time · executed on M15 · no fees",
                asset.equals("btcusdt") ? "Binance" : symbol.code(),
                tradeFrom != null ? tradeFrom.toLocalDate() : m15.getFirst().openTime().toLocalDate(),
                tradeTo != null ? tradeTo.toLocalDate() : m15.getLast().closeTime().toLocalDate(), sideArg,
                d1Bias ? " · only when the D1 structure trend points the trade's way" : "",
                INITIAL_CAPITAL, RISK_PER_TRADE * 100, SL_LOOKBACK,
                minStopAtr > 0 ? String.format(Locale.ROOT, ", at least %.1f ATR", minStopAtr) : "",
                LEG_LOOKBACK, selection.name().toLowerCase(Locale.ROOT), MIN_REWARD_TO_RISK,
                breakEvenAtR > 0 ? String.format(Locale.ROOT, " · SL to entry after +%.1fR", breakEvenAtR) : "",
                limit ? "entry: limit at the near edge of the closest open FVG between SL and alert ("
                        + (hasM5 ? "M15, else M5, " : "M15, ") + "else market), cancelled after " + LIMIT_TTL.toHours() + "h or once TP trades"
                        : "entry: market at next open");

        TradeReportWriter report = new TradeReportWriter();
        report.writeTradesCsv(result.trades(), Paths.get(base + ".csv"));
        report.writeSignalsCsv(result.signals(), Paths.get(base + "_signals.csv"));
        report.writeHtml(title, settings, stats, result.trades(), result.signals(), Paths.get(base + ".html"));
        new TradesPineExporter().export(result.trades(), result.signals(), Timeframe.H1, title, Paths.get(base + ".pine"));

        log.info("{} | trades {} | win rate {}% | PF {} | net ${} ({}%) | max DD {}% | avg R {}",
                title, stats.trades(), fmt(stats.winRatePct()), fmt(stats.profitFactor()), fmt(stats.netPnl()),
                fmt(stats.returnPct()), fmt(stats.maxDrawdownPct()), fmt(stats.avgR()));
    }

    /**
     * One side's view of the market. For shorts every candle is mirrored before it reaches the
     * trackers, so their "bearish targets above" and "bullish entries below" are the real bullish
     * targets below and bearish entries above.
     */
    private static final class Book {
        final Direction side;
        final List<PriceCandle> h1;
        final HtfFvgTracker targets = new HtfFvgTracker();
        final BullishFvgTracker entriesM15 = new BullishFvgTracker(ENTRY_FVG_MAX_AGE);
        final BullishFvgTracker entriesM5 = new BullishFvgTracker(ENTRY_FVG_MAX_AGE);
        final InversionFvgTracker inversionsM15 = new InversionFvgTracker(ENTRY_FVG_MAX_AGE);
        final InversionFvgTracker inversionsM5 = new InversionFvgTracker(ENTRY_FVG_MAX_AGE);
        /** M15 structure on this side's view, so an MSS to UP is a bullish one for longs, bearish for shorts. */
        final MarketStructureTracker structureM15 = new MarketStructureTracker(true);
        boolean mssOnLastBar;

        Book(Direction side, List<PriceCandle> realH1) {
            this.side = side;
            this.h1 = side == Direction.LONG ? realH1 : realH1.stream().map(PriceMirror::candle).toList();
        }

        PriceCandle view(PriceCandle real) {
            return side == Direction.LONG ? real : PriceMirror.candle(real);
        }

        Trade real(Trade trade) {
            return side == Direction.LONG ? trade : PriceMirror.shortTrade(trade);
        }

        SignalPlan real(SignalPlan plan) {
            return side == Direction.LONG ? plan : PriceMirror.shortPlan(plan);
        }
    }

    /**
     * Where a limit rests in its entry zone.
     *
     * @param fvgAtExtreme  a bullish FVG is bought at its far ({@code lower}) edge instead of the near one
     * @param ifvg          inverted bearish FVGs are entry zones too; when both kinds qualify, the zone
     *                      closer below the alert wins
     * @param ifvgAtExtreme an IFVG is bought at its far ({@code lower}) edge instead of the near one
     * @param deeperOfBoth  the closest zone is picked on M15 and on M5 alike and the limit goes to the
     *                      lower of their two prices, instead of M5 only when M15 has no zone
     */
    record EntryRule(boolean fvgAtExtreme, boolean ifvg, boolean ifvgAtExtreme, boolean deeperOfBoth) {

        /** A bullish FVG only, at its near edge — the entry every run used before this rule existed. */
        static final EntryRule NEAR_EDGE = new EntryRule(false, false, false, false);

        /** The chosen entry: a bullish FVG at its far edge, an IFVG at its near edge, M15 before M5. */
        static final EntryRule BASE = new EntryRule(true, true, false, false);

        EntryRule(boolean fvgAtExtreme, boolean ifvg) {
            this(fvgAtExtreme, ifvg, false, false);
        }

        String label() {
            return (deeperOfBoth ? "deeper-m15-m5_" : "") + (fvgAtExtreme ? "fvg-extreme" : "fvg-near")
                    + (ifvg ? (ifvgAtExtreme ? "_ifvg-extreme" : "_ifvg-near") : "");
        }
    }

    /**
     * An order waiting for its fill: a market order fills at the next open, a limit one at {@code price}.
     * With {@code awaitingMss} it is not an order yet — it becomes a limit once M15 structure shifts, or
     * lapses at {@code expiresAt}.
     */
    private record PendingEntry(Book book, SignalPlan plan, int signalIndex, Double price, String type,
                                ZonedDateTime expiresAt, boolean awaitingMss) {

        PendingEntry(Book book, SignalPlan plan, int signalIndex, Double price, String type, ZonedDateTime expiresAt) {
            this(book, plan, signalIndex, price, type, expiresAt, false);
        }
    }

    /**
     * Replays M15 as the execution clock. After each M15 bar closes, every H1, H4 and M5 candle that
     * closed by then is applied, so an H1 signal sees exactly the zones known at its close and its order
     * starts working on the next M15 bar.
     */
    static DoubleDivergenceTradeBacktest.Result run(Symbol symbol, List<PriceCandle> h1, List<PriceCandle> m15,
                                                    List<PriceCandle> m5, List<PriceCandle> htfByCloseTime,
                                                    LongTradePlanner planner, boolean limit, double breakEvenAtR,
                                                    Set<Direction> sides) {
        return run(symbol, h1, m15, m5, htfByCloseTime, planner, limit, breakEvenAtR, sides, null, null);
    }

    /**
     * As above, for any signal timeframe ({@code h1} may hold M15, M30, H2… candles), acting only on
     * alerts whose bar closes in {@code [tradeFrom, tradeTo)}; null leaves that side open. Earlier bars
     * warm up the indicators and trackers, and positions opened inside the window run to their exit.
     */
    static DoubleDivergenceTradeBacktest.Result run(Symbol symbol, List<PriceCandle> h1, List<PriceCandle> m15,
                                                    List<PriceCandle> m5, List<PriceCandle> htfByCloseTime,
                                                    LongTradePlanner planner, boolean limit, double breakEvenAtR,
                                                    Set<Direction> sides, ZonedDateTime tradeFrom,
                                                    ZonedDateTime tradeTo) {
        return run(symbol, h1, m15, m5, htfByCloseTime, planner, limit, breakEvenAtR, sides, tradeFrom, tradeTo,
                EntryRule.NEAR_EDGE);
    }

    /** As above, with the limit placed by {@code rule}. */
    static DoubleDivergenceTradeBacktest.Result run(Symbol symbol, List<PriceCandle> h1, List<PriceCandle> m15,
                                                    List<PriceCandle> m5, List<PriceCandle> htfByCloseTime,
                                                    LongTradePlanner planner, boolean limit, double breakEvenAtR,
                                                    Set<Direction> sides, ZonedDateTime tradeFrom,
                                                    ZonedDateTime tradeTo, EntryRule rule) {
        return run(symbol, h1, m15, m5, htfByCloseTime, planner, limit, breakEvenAtR, sides, tradeFrom, tradeTo,
                rule, NO_BIAS, false);
    }

    /** Lets every alert through: no higher-timeframe bias. */
    static final BiPredicate<Direction, ZonedDateTime> NO_BIAS = (side, time) -> true;

    /**
     * As above, acting only on alerts {@code bias} accepts for their side and the close of their bar.
     * Rejected alerts are not planned and leave no trace in the signal list.
     *
     * <p>With {@code afterMss} (limit mode only) a planned alert places nothing yet: it waits up to
     * {@link #LIMIT_TTL} for an M15 MSS in its direction after the alert. The MSS moves SL from the
     * planner's H1 extreme to the M15 protected low it left, and the limit rests on the closest M15 FVG
     * (M5 when M15 has none) between that SL and the MSS bar's close, for another {@link #LIMIT_TTL};
     * R:R is re-measured from that close, TP stays the planner's. Before the MSS the alert lapses as
     * {@link SignalOutcome#INVALIDATED} if the H1 SL trades, {@link SignalOutcome#LIMIT_MISSED} if TP
     * does and {@link SignalOutcome#NO_MSS} on timeout; {@link SignalOutcome#NO_ENTRY_ZONE} when the MSS
     * leaves no zone.
     */
    static DoubleDivergenceTradeBacktest.Result run(Symbol symbol, List<PriceCandle> h1, List<PriceCandle> m15,
                                                    List<PriceCandle> m5, List<PriceCandle> htfByCloseTime,
                                                    LongTradePlanner planner, boolean limit, double breakEvenAtR,
                                                    Set<Direction> sides, ZonedDateTime tradeFrom,
                                                    ZonedDateTime tradeTo, EntryRule rule,
                                                    BiPredicate<Direction, ZonedDateTime> bias, boolean afterMss) {
        InMemoryDivergenceRepository divergenceRepo = new InMemoryDivergenceRepository();
        DoubleDivergenceScenario scenario = new DoubleDivergenceScenario(divergenceRepo);
        WaveTrendIndicator wt = new WaveTrendIndicator(9, 12, 3);
        WtDivergenceDetector detector = new WtDivergenceDetector();
        List<Book> books = sides.stream().map(side -> new Book(side, h1)).toList();

        // Plans are stored as simulated (mirrored for shorts) and mapped back once the run is over.
        List<SignalPlan> signals = new ArrayList<>();
        List<Book> signalBooks = new ArrayList<>();
        List<Trade> trades = new ArrayList<>();
        double equity = INITIAL_CAPITAL;
        int h1Index = 0, htfIndex = 0, m5Index = 0;
        PendingEntry pending = null;
        OpenPosition position = null;
        Book positionBook = null;

        for (PriceCandle realBar : m15) {
            if (pending != null && pending.awaitingMss()) {
                PriceCandle bar = pending.book().view(realBar);
                SignalPlan plan = pending.plan();
                SignalOutcome lapsed = !bar.openTime().isBefore(pending.expiresAt()) ? SignalOutcome.NO_MSS
                        : bar.low().doubleValue() <= plan.stopLoss() ? SignalOutcome.INVALIDATED
                        : bar.high().doubleValue() >= plan.takeProfit() ? SignalOutcome.LIMIT_MISSED
                        : null;
                if (lapsed != null) {
                    signals.set(pending.signalIndex(), plan.withOutcome(lapsed));
                    pending = null;
                }
            } else if (pending != null) {
                Book book = pending.book();
                PriceCandle bar = book.view(realBar);
                double risk = equity * RISK_PER_TRADE;
                SignalPlan plan = pending.plan();
                Optional<Trade> exit = Optional.empty();
                if (pending.price() == null) {
                    Optional<OpenPosition> entered = OpenPosition.enter(plan, bar, risk, breakEvenAtR);
                    signals.set(pending.signalIndex(), plan.withOutcome(
                            entered.isPresent() ? SignalOutcome.TRADED : SignalOutcome.GAP_SKIPPED));
                    position = entered.orElse(null);
                    pending = null;
                    if (position != null) {
                        exit = position.exitOn(bar, equity);
                    }
                } else if (!bar.openTime().isBefore(pending.expiresAt())) {
                    signals.set(pending.signalIndex(), plan.withOutcome(SignalOutcome.LIMIT_EXPIRED));
                    pending = null;
                } else {
                    double limitPrice = pending.price();
                    double open = bar.open().doubleValue();
                    if (open <= limitPrice) {
                        // Gapped through the limit: filled at the open, which is a better price —
                        // unless it opened beyond SL too, then the order filled at the limit on the way.
                        double fill = open > plan.stopLoss() ? open : limitPrice;
                        position = OpenPosition.fillLimit(plan, bar, fill, risk, breakEvenAtR, pending.type());
                        exit = position.exitOn(bar, equity);
                    } else if (bar.low().doubleValue() <= limitPrice) {
                        position = OpenPosition.fillLimit(plan, bar, limitPrice, risk, breakEvenAtR, pending.type());
                        exit = position.exitOnFillBar(bar, equity);
                    } else if (bar.high().doubleValue() >= plan.takeProfit()) {
                        signals.set(pending.signalIndex(), plan.withOutcome(SignalOutcome.LIMIT_MISSED));
                        pending = null;
                    }
                    if (position != null) {
                        signals.set(pending.signalIndex(), plan.withOutcome(SignalOutcome.TRADED));
                        pending = null;
                    }
                }
                if (position != null) {
                    positionBook = book;
                }
                if (exit.isPresent()) {
                    trades.add(book.real(exit.get()));
                    equity = exit.get().equityAfter();
                    position = null;
                }
            } else if (position != null) {
                Optional<Trade> exit = position.exitOn(positionBook.view(realBar), equity);
                if (exit.isPresent()) {
                    trades.add(positionBook.real(exit.get()));
                    equity = exit.get().equityAfter();
                    position = null;
                }
            }

            // The bar has closed: bring every tracker up to this moment.
            for (Book book : books) {
                PriceCandle bar = book.view(realBar);
                book.targets.onPrice(bar.high().doubleValue());
                book.entriesM15.onCandle(bar);
                book.inversionsM15.onCandle(bar);
                if (afterMss) {
                    book.mssOnLastBar = book.structureM15.onCandle(bar).stream()
                            .anyMatch(e -> e.type() == StructureEventType.MSS && e.trend() == Trend.UP);
                }
            }
            while (m5Index < m5.size() && !m5.get(m5Index).closeTime().isAfter(realBar.closeTime())) {
                PriceCandle candle = m5.get(m5Index++);
                for (Book book : books) {
                    book.entriesM5.onCandle(book.view(candle));
                    book.inversionsM5.onCandle(book.view(candle));
                }
            }
            while (htfIndex < htfByCloseTime.size() && !htfByCloseTime.get(htfIndex).closeTime().isAfter(realBar.closeTime())) {
                PriceCandle candle = htfByCloseTime.get(htfIndex++);
                for (Book book : books) {
                    book.targets.onHtfCandle(book.view(candle));
                }
            }

            // Before this bar's alerts are read, so an alert only ever waits for a later MSS.
            if (pending != null && pending.awaitingMss() && pending.book().mssOnLastBar) {
                Book book = pending.book();
                double ceiling = book.view(realBar).close().doubleValue();
                double stop = book.structureM15.protectedLevel();
                SignalPlan plan = pending.plan().withStopLoss(stop,
                        (pending.plan().takeProfit() - ceiling) / (ceiling - stop));
                ZonedDateTime expiresAt = realBar.closeTime().plus(LIMIT_TTL);
                int index = pending.signalIndex();
                signals.set(index, plan);
                Optional<PendingEntry> m15Order = limitOrder(book, plan, index, rule,
                        book.entriesM15, book.inversionsM15, "M15_MSS", ceiling, expiresAt);
                Optional<PendingEntry> m5Order = m15Order.isEmpty() || rule.deeperOfBoth()
                        ? limitOrder(book, plan, index, rule, book.entriesM5, book.inversionsM5, "M5_MSS", ceiling, expiresAt)
                        : Optional.empty();
                Optional<PendingEntry> order = Stream.of(m15Order, m5Order).flatMap(Optional::stream)
                        .min(Comparator.comparingDouble(PendingEntry::price));
                if (order.isEmpty()) {
                    signals.set(index, plan.withOutcome(SignalOutcome.NO_ENTRY_ZONE));
                }
                pending = order.orElse(null);
            }

            while (h1Index < h1.size() && !h1.get(h1Index).closeTime().isAfter(realBar.closeTime())) {
                int i = h1Index++;
                PriceCandle signalBar = h1.get(i);
                double wt2 = wt.next(PriceCandleUtils.hlc3(signalBar)).wt2();
                Optional<Direction> div = detector.next(wt2, signalBar.high().doubleValue(), signalBar.low().doubleValue());
                if (div.isEmpty()) {
                    continue;
                }
                boolean tradable = !signalBar.closeTime().isBefore(m15.getFirst().closeTime())
                        && (tradeFrom == null || !signalBar.closeTime().isBefore(tradeFrom))
                        && (tradeTo == null || signalBar.closeTime().isBefore(tradeTo));
                DivergenceSignal saved = divergenceRepo.save(
                        new DivergenceSignal(null, symbol, signalBar.timeframe(), div.get(), 1.0, signalBar.closeTime()));
                for (AlertToSend alert : scenario.onEvent(new DivergenceDetectedEvent(saved))) {
                    if (!tradable || !bias.test(alert.getDirection(), signalBar.closeTime())) {
                        continue;
                    }
                    Optional<Book> side = books.stream().filter(b -> b.side == alert.getDirection()).findFirst();
                    if (side.isEmpty()) {
                        continue;
                    }
                    Book book = side.get();
                    SignalPlan plan = planner.plan(book.h1, i, book.targets.openZones());
                    if ((position != null || pending != null) && plan.outcome() == SignalOutcome.PLANNED) {
                        plan = plan.withOutcome(SignalOutcome.POSITION_OPEN);
                    }
                    signals.add(plan);
                    signalBooks.add(book);
                    if (plan.outcome() != SignalOutcome.PLANNED) {
                        continue;
                    }
                    int index = signals.size() - 1;
                    SignalPlan planned = plan;
                    Optional<PendingEntry> order = Optional.empty();
                    if (afterMss) {
                        pending = new PendingEntry(book, planned, index, null, "AWAIT_MSS",
                                planned.signalTime().plus(LIMIT_TTL), true);
                        continue;
                    }
                    if (limit) {
                        Optional<PendingEntry> m15Order =
                                limitOrder(book, planned, index, rule, book.entriesM15, book.inversionsM15, "M15");
                        Optional<PendingEntry> m5Order = m15Order.isEmpty() || rule.deeperOfBoth()
                                ? limitOrder(book, planned, index, rule, book.entriesM5, book.inversionsM5, "M5")
                                : Optional.empty();
                        order = Stream.of(m15Order, m5Order).flatMap(Optional::stream)
                                .min(Comparator.comparingDouble(PendingEntry::price));
                    }
                    pending = order.orElseGet(() -> new PendingEntry(book, planned, index, null, "MARKET", null));
                }
            }
        }

        if (position != null) {
            trades.add(positionBook.real(position.markOpenAtEnd(positionBook.view(m15.getLast()), equity)));
        }
        List<SignalPlan> realSignals = new ArrayList<>();
        for (int i = 0; i < signals.size(); i++) {
            realSignals.add(signalBooks.get(i).real(signals.get(i)));
        }
        return new DoubleDivergenceTradeBacktest.Result(trades, realSignals);
    }

    /** A limit on one timeframe's zones under {@code rule}, or empty when that timeframe has none. */
    private static Optional<PendingEntry> limitOrder(Book book, SignalPlan plan, int index, EntryRule rule,
                                                     BullishFvgTracker fvgs, InversionFvgTracker ifvgs, String tf) {
        return limitOrder(book, plan, index, rule, fvgs, ifvgs, tf, plan.close(), plan.signalTime().plus(LIMIT_TTL));
    }

    /** As above, for zones below {@code ceiling} instead of the alert's close, working until {@code expiresAt}. */
    private static Optional<PendingEntry> limitOrder(Book book, SignalPlan plan, int index, EntryRule rule,
                                                     BullishFvgTracker fvgs, InversionFvgTracker ifvgs, String tf,
                                                     double ceiling, ZonedDateTime expiresAt) {
        Optional<OpenFvg> fvg = fvgs.highestBetween(plan.stopLoss(), ceiling);
        Optional<OpenFvg> ifvg = rule.ifvg() ? ifvgs.highestBetween(plan.stopLoss(), ceiling) : Optional.empty();
        if (fvg.isEmpty() && ifvg.isEmpty()) {
            return Optional.empty();
        }
        boolean inverted = ifvg.isPresent() && (fvg.isEmpty() || ifvg.get().upper() > fvg.get().upper());
        double price = inverted ? (rule.ifvgAtExtreme() ? ifvg.get().lower() : ifvg.get().upper())
                : rule.fvgAtExtreme() ? fvg.get().lower() : fvg.get().upper();
        // The default rule keeps the type names older reports were written with.
        String type = "LIMIT_" + tf + (rule.equals(EntryRule.NEAR_EDGE) ? "" : inverted ? "_IFVG" : "_FVG");
        return Optional.of(new PendingEntry(book, plan, index, price, type, expiresAt));
    }

    /** The UTC midnight of a {@code name=YYYY-MM-DD} flag, or null when it is absent. */
    private static ZonedDateTime dateFlag(List<String> flags, String name) {
        return flags.stream().filter(f -> f.startsWith(name)).findFirst()
                .map(f -> java.time.LocalDate.parse(f.substring(name.length())).atStartOfDay(java.time.ZoneOffset.UTC))
                .orElse(null);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
