package pl.tradeengine.backtest;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.backtest.export.TradeReportWriter;
import pl.tradeengine.backtest.export.TradesPineExporter;
import pl.tradeengine.backtest.indicators.WaveTrendIndicator;
import pl.tradeengine.backtest.indicators.WtDivergenceDetector;
import pl.tradeengine.backtest.loader.HistoricalCandleLoader;
import pl.tradeengine.backtest.repository.InMemoryDivergenceRepository;
import pl.tradeengine.backtest.trade.HtfFvgTracker;
import pl.tradeengine.backtest.trade.LongTradePlanner;
import pl.tradeengine.backtest.trade.OpenPosition;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DOUBLE_DIVERGENCE_STRATEGY traded long-only, with SL and TP — the first backtest that produces
 * trades and an equity curve rather than signal markers.
 *
 * <p>Signals come from the same path as {@link DoubleDivergenceBacktest}. Each long signal is
 * planned by {@link LongTradePlanner} against the open HTF bearish FVGs known at that moment
 * ({@link HtfFvgTracker}), entered at the next bar's open and held until SL or TP — nothing else
 * closes it. One position at a time; risk is a fixed share of the current balance.
 *
 * <p>Usage: {@code DoubleDivergenceTradeBacktest [timeframe] [asset] [minStopAtr] [targets] [breakEvenAtR] [highest|nearest]};
 * defaults M30, nasdaq, 0 (SL purely from the swing low) and {@code H4,H1} — the FVG timeframes
 * searched for TP, in order. Reads {@code data/{asset}_{tf}.csv} plus one file per target
 * timeframe, writes to {@code output/}. {@code breakEvenAtR} (0 = off) moves SL to entry once price
 * has gained that many R. A non-default {@code minStopAtr}, {@code targets} or {@code breakEvenAtR} gets
 * its own file suffix, so variants can sit side by side.
 */
@Slf4j
public class DoubleDivergenceTradeBacktest {

    static final double INITIAL_CAPITAL = 10_000;
    static final double RISK_PER_TRADE = 0.01;
    static final int SL_LOOKBACK = 10;
    static final int LEG_LOOKBACK = 100;
    static final double MIN_REWARD_TO_RISK = 1.5;
    static final List<Timeframe> DEFAULT_TARGET_TIMEFRAMES = List.of(Timeframe.H4, Timeframe.H1);
    static final int ATR_PERIOD = 14;

    public static void main(String[] args) throws IOException {
        Timeframe tf = args.length > 0 ? Timeframe.valueOf(args[0].toUpperCase(Locale.ROOT)) : Timeframe.M30;
        String asset = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "nasdaq";
        double minStopAtr = args.length > 2 ? Double.parseDouble(args[2]) : 0;
        List<Timeframe> targets = args.length > 3
                ? Arrays.stream(args[3].split(",")).map(t -> Timeframe.valueOf(t.trim().toUpperCase(Locale.ROOT))).toList()
                : DEFAULT_TARGET_TIMEFRAMES;
        double breakEvenAtR = args.length > 4 ? Double.parseDouble(args[4]) : 0;
        TargetSelection selection = args.length > 5
                ? TargetSelection.valueOf(args[5].toUpperCase(Locale.ROOT)) : TargetSelection.HIGHEST;
        Symbol symbol = new Symbol(asset.toUpperCase(Locale.ROOT));

        HistoricalCandleLoader loader = new HistoricalCandleLoader();
        List<PriceCandle> candles = loader.loadFromCsv(csv(asset, tf), symbol, tf);
        List<PriceCandle> htf = new ArrayList<>();
        for (Timeframe t : targets) {
            htf.addAll(loader.loadFromCsv(csv(asset, t), symbol, t));
        }
        htf.sort(Comparator.comparing(PriceCandle::closeTime));

        Result result = run(symbol, candles, htf, minStopAtr, targets, breakEvenAtR, selection);
        TradeStatistics stats = TradeStatistics.of(result.trades(), INITIAL_CAPITAL);

        String atrSuffix = minStopAtr > 0 ? String.format(Locale.ROOT, "_minsl%.1fatr", minStopAtr) : "";
        String targetSuffix = targets.equals(DEFAULT_TARGET_TIMEFRAMES) ? ""
                : "_tp-" + targets.stream().map(t -> t.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining("-"));
        String base = "output/" + asset + "_dd_trades_" + tf.name().toLowerCase(Locale.ROOT) + atrSuffix + targetSuffix
                + (breakEvenAtR > 0 ? String.format(Locale.ROOT, "_be%.1fr", breakEvenAtR) : "")
                + (selection == TargetSelection.HIGHEST ? "" : "_" + selection.name().toLowerCase(Locale.ROOT));
        String title = "DD " + tf + " " + symbol.code() + " long · SL/TP FVG " + targets
                + (minStopAtr > 0 ? String.format(Locale.ROOT, " · SL ≥ %.1f ATR", minStopAtr) : "")
                + (breakEvenAtR > 0 ? String.format(Locale.ROOT, " · BE at %.1fR", breakEvenAtR) : "")
                + (selection == TargetSelection.HIGHEST ? "" : " · " + selection.name().toLowerCase(Locale.ROOT) + " FVG");
        String stop = minStopAtr > 0
                ? String.format(Locale.ROOT, "SL low of %d bars, at least %.1f × ATR(%d) below close",
                        SL_LOOKBACK, minStopAtr, ATR_PERIOD)
                : String.format(Locale.ROOT, "SL low of %d bars", SL_LOOKBACK);
        String settings = String.format(Locale.ROOT,
                "%s → %s · capital $%,.0f · risk %.1f%% · %s · leg = high of %d bars · "
                        + "TP = top of %s open FVG in leg (%s) · min R:R %.1f%s · no fees",
                candles.getFirst().openTime().toLocalDate(), candles.getLast().closeTime().toLocalDate(),
                INITIAL_CAPITAL, RISK_PER_TRADE * 100, stop, LEG_LOOKBACK, selection.name().toLowerCase(Locale.ROOT), targets,
                MIN_REWARD_TO_RISK,
                breakEvenAtR > 0 ? String.format(Locale.ROOT, " · SL to entry after +%.1fR", breakEvenAtR) : "");

        TradeReportWriter report = new TradeReportWriter();
        report.writeTradesCsv(result.trades(), Paths.get(base + ".csv"));
        report.writeSignalsCsv(result.signals(), Paths.get(base + "_signals.csv"));
        report.writeHtml(title, settings, stats, result.trades(), result.signals(), Paths.get(base + ".html"));
        new TradesPineExporter().export(result.trades(), result.signals(), tf, title, Paths.get(base + ".pine"));

        log.info("{} | trades {} | win rate {}% | PF {} | net ${} ({}%) | max DD {}% | avg R {}",
                title, stats.trades(), fmt(stats.winRatePct()), fmt(stats.profitFactor()), fmt(stats.netPnl()),
                fmt(stats.returnPct()), fmt(stats.maxDrawdownPct()), fmt(stats.avgR()));
    }

    /**
     * Replays {@code candles} (the trigger timeframe) and, interleaved by close time, the HTF
     * candles that feed the FVG tracker. An HTF candle is applied once it closed at or before the
     * trigger bar's close, so a signal never sees a zone or a fill from its future.
     */
    static Result run(Symbol symbol, List<PriceCandle> candles, List<PriceCandle> htfByCloseTime, double minStopAtr,
                      List<Timeframe> targets, double breakEvenAtR, TargetSelection selection) {
        InMemoryDivergenceRepository divergenceRepo = new InMemoryDivergenceRepository();
        DoubleDivergenceScenario scenario = new DoubleDivergenceScenario(divergenceRepo);
        WaveTrendIndicator wt = new WaveTrendIndicator(9, 12, 3);
        WtDivergenceDetector detector = new WtDivergenceDetector();
        HtfFvgTracker fvgs = new HtfFvgTracker();
        LongTradePlanner planner = new LongTradePlanner(
                SL_LOOKBACK, LEG_LOOKBACK, MIN_REWARD_TO_RISK, targets, minStopAtr, ATR_PERIOD, selection);

        List<SignalPlan> signals = new ArrayList<>();
        List<Trade> trades = new ArrayList<>();
        double equity = INITIAL_CAPITAL;
        int htfIndex = 0;
        SignalPlan pending = null;
        int pendingIndex = -1;
        OpenPosition position = null;

        for (int i = 0; i < candles.size(); i++) {
            PriceCandle bar = candles.get(i);

            if (pending != null) {
                Optional<OpenPosition> entered = OpenPosition.enter(pending, bar, equity * RISK_PER_TRADE, breakEvenAtR);
                signals.set(pendingIndex, pending.withOutcome(entered.isPresent() ? SignalOutcome.TRADED : SignalOutcome.GAP_SKIPPED));
                position = entered.orElse(null);
                pending = null;
            }
            if (position != null) {
                Optional<Trade> exit = position.exitOn(bar, equity);
                if (exit.isPresent()) {
                    trades.add(exit.get());
                    equity = exit.get().equityAfter();
                    position = null;
                }
            }

            while (htfIndex < htfByCloseTime.size() && !htfByCloseTime.get(htfIndex).closeTime().isAfter(bar.closeTime())) {
                fvgs.onHtfCandle(htfByCloseTime.get(htfIndex++));
            }
            fvgs.onPrice(bar.high().doubleValue());

            double wt2 = wt.next(PriceCandleUtils.hlc3(bar)).wt2();
            Optional<Direction> div = detector.next(wt2, bar.high().doubleValue(), bar.low().doubleValue());
            if (div.isEmpty()) {
                continue;
            }
            DivergenceSignal saved = divergenceRepo.save(
                    new DivergenceSignal(null, symbol, bar.timeframe(), div.get(), 1.0, bar.closeTime()));
            for (AlertToSend alert : scenario.onEvent(new DivergenceDetectedEvent(saved))) {
                if (alert.getDirection() != Direction.LONG) {
                    continue;
                }
                SignalPlan plan = planner.plan(candles, i, fvgs.openZones());
                if (position != null && plan.outcome() == SignalOutcome.PLANNED) {
                    plan = plan.withOutcome(SignalOutcome.POSITION_OPEN);
                }
                signals.add(plan);
                if (plan.outcome() == SignalOutcome.PLANNED && i + 1 < candles.size()) {
                    pending = plan;
                    pendingIndex = signals.size() - 1;
                }
            }
        }

        if (position != null) {
            trades.add(position.markOpenAtEnd(candles.getLast(), equity));
        }
        return new Result(trades, signals);
    }

    record Result(List<Trade> trades, List<SignalPlan> signals) {
    }

    private static Path csv(String asset, Timeframe tf) {
        return Paths.get("data/" + asset + "_" + tf.name().toLowerCase(Locale.ROOT) + ".csv");
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
