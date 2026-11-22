package pl.tradeengine.backtest.export;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.backtest.trade.ExitReason;
import pl.tradeengine.backtest.trade.SignalOutcome;
import pl.tradeengine.backtest.trade.SignalPlan;
import pl.tradeengine.backtest.trade.Trade;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.Timeframe;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pine Script overlay of simulated trades: B under the entry bar, TP/SL/BE on the exit bar, and a box
 * per trade spanning entry→exit — green up to TP, red down to SL, like TradingView's position tool.
 * Signals without a target get a grey "?" on the signal bar, for manual review.
 *
 * <p>Shorts mirror longs: S above the entry bar instead of B below it, the alert's A above its bar.
 *
 * <p>The alert gets its own marker: a blue A on the signal bar, apart from B on the entry bar, with a
 * dotted line at the limit price from alert to fill when the entry was a limit order. An alert whose
 * limit never filled gets a grey A.
 *
 * <p>Markers match on bar open time, so they show up only on the backtest's own timeframe; boxes
 * are drawn by time and show on any timeframe. TradingView caps boxes at 500, so only the last
 * {@value #MAX_TRADES} trades are exported.
 */
@Slf4j
public class TradesPineExporter {

    static final int MAX_TRADES = 240;
    private static final int PUSHES_PER_BLOCK = 100;

    public void export(List<Trade> allTrades, List<SignalPlan> signals, Timeframe timeframe,
                       String title, Path outputPath) throws IOException {
        List<Trade> trades = allTrades.size() > MAX_TRADES
                ? allTrades.subList(allTrades.size() - MAX_TRADES, allTrades.size())
                : allTrades;
        long firstEntry = trades.isEmpty() ? Long.MIN_VALUE : ms(trades.getFirst().entryTime());
        List<Long> review = signals.stream()
                .filter(s -> s.outcome() == SignalOutcome.NO_TARGET)
                .map(s -> ms(s.signalTime().minus(timeframe.getDuration())))
                .filter(t -> t >= firstEntry)
                .toList();
        List<Long> unfilled = signals.stream()
                .filter(s -> switch (s.outcome()) {
                    case LIMIT_EXPIRED, LIMIT_MISSED, NO_MSS, NO_ENTRY_ZONE, INVALIDATED -> true;
                    default -> false;
                })
                .map(s -> ms(s.signalTime().minus(timeframe.getDuration())))
                .filter(t -> t >= firstEntry)
                .toList();

        try (BufferedWriter w = Files.newBufferedWriter(outputPath)) {
            w.write("//@version=5\n");
            w.write("indicator('" + title + " (" + trades.size() + " trades)', overlay=true, "
                    + "max_boxes_count=500, max_labels_count=500, max_lines_count=500)\n\n");

            w.write("var entryT = array.new_int()\n");
            w.write("var exitT = array.new_int()\n");
            w.write("var entryPx = array.new_float()\n");
            w.write("var slPx = array.new_float()\n");
            w.write("var tpPx = array.new_float()\n");
            w.write("var result = array.new_int()   // 1 = TP, -1 = SL, 2 = SL moved to entry, 0 = still open\n");
            w.write("var reviewT = array.new_int()\n");
            w.write("var alertT = array.new_int()   // signal bar of each trade\n");
            w.write("var isLimit = array.new_bool()\n");
            w.write("var isShort = array.new_bool()\n");
            w.write("var unfilledT = array.new_int()\n");
            List<String> pushes = new ArrayList<>();
            for (Trade t : trades) {
                int result = switch (t.exitReason()) {
                    case TP -> 1;
                    case SL -> -1;
                    case BE -> 2;
                    case OPEN_AT_END -> 0;
                };
                // A limit fill on M15 opens mid-bar on an H1 chart: snap to the chart bar so markers match.
                pushes.add("array.push(entryT, " + barStart(t.entryTime(), timeframe) + ")");
                pushes.add("array.push(exitT, " + barStart(t.exitTime(), timeframe) + ")");
                pushes.add(String.format(Locale.ROOT, "array.push(entryPx, %.4f)", t.entryPrice()));
                pushes.add(String.format(Locale.ROOT, "array.push(slPx, %.4f)", t.stopLoss()));
                pushes.add(String.format(Locale.ROOT, "array.push(tpPx, %.4f)", t.takeProfit()));
                pushes.add("array.push(result, " + result + ")");
                pushes.add("array.push(alertT, " + ms(t.signalTime().minus(timeframe.getDuration())) + ")");
                pushes.add("array.push(isLimit, " + t.entryType().startsWith("LIMIT") + ")");
                pushes.add("array.push(isShort, " + (t.direction() == Direction.SHORT) + ")");
            }
            for (Long t : unfilled) {
                pushes.add("array.push(unfilledT, " + t + ")");
            }
            for (Long t : review) {
                pushes.add("array.push(reviewT, " + t + ")");
            }
            // Pine rejects one long if-block ("the if statement is too long"), so the pushes go into
            // many short ones; each still runs once, on the first bar.
            for (int i = 0; i < pushes.size(); i++) {
                if (i % PUSHES_PER_BLOCK == 0) {
                    w.write("if barstate.isfirst\n");
                }
                w.write("    " + pushes.get(i) + "\n");
            }

            w.write("\nentryIdx = array.indexof(entryT, time)\n");
            w.write("entryShort = entryIdx >= 0 ? array.get(isShort, entryIdx) : false\n");
            w.write("isEntry = entryIdx >= 0 and not entryShort\n");
            w.write("isEntryShort = entryIdx >= 0 and entryShort\n");
            w.write("exitIdx = array.indexof(exitT, time)\n");
            // v5 evaluates both sides of `and`, so the index guard must be a ternary.
            w.write("exitResult = exitIdx >= 0 ? array.get(result, exitIdx) : 0\n");
            w.write("isTp = exitResult == 1\n");
            w.write("isSl = exitResult == -1\n");
            w.write("isBe = exitResult == 2\n");
            w.write("isReview = array.includes(reviewT, time)\n");
            w.write("alertIdx = array.indexof(alertT, time)\n");
            w.write("alertShort = alertIdx >= 0 ? array.get(isShort, alertIdx) : false\n");
            w.write("isAlert = alertIdx >= 0 and not alertShort\n");
            w.write("isAlertShort = alertIdx >= 0 and alertShort\n");
            w.write("isUnfilled = array.includes(unfilledT, time)\n\n");
            w.write("plotshape(isAlert, title='Alert', style=shape.labelup, location=location.belowbar, "
                    + "color=color.new(color.blue, 0), text='A', textcolor=color.white, size=size.tiny)\n");
            w.write("plotshape(isAlertShort, title='Alert short', style=shape.labeldown, location=location.abovebar, "
                    + "color=color.new(color.blue, 0), text='A', textcolor=color.white, size=size.tiny)\n");
            w.write("plotshape(isUnfilled, title='Alert, limit not filled', style=shape.labelup, location=location.belowbar, "
                    + "color=color.new(color.gray, 0), text='A', textcolor=color.white, size=size.tiny)\n");

            w.write("plotshape(isEntry, title='Entry', style=shape.labelup, location=location.belowbar, "
                    + "color=color.new(color.green, 0), text='B', textcolor=color.white, size=size.tiny)\n");
            w.write("plotshape(isEntryShort, title='Entry short', style=shape.labeldown, location=location.abovebar, "
                    + "color=color.new(color.maroon, 0), text='S', textcolor=color.white, size=size.tiny)\n");
            w.write("plotshape(isTp, title='Exit TP', style=shape.labeldown, location=location.abovebar, "
                    + "color=color.new(color.teal, 0), text='TP', textcolor=color.white, size=size.tiny)\n");
            w.write("plotshape(isSl, title='Exit SL', style=shape.labeldown, location=location.abovebar, "
                    + "color=color.new(color.red, 0), text='SL', textcolor=color.white, size=size.tiny)\n");
            w.write("plotshape(isBe, title='Exit BE', style=shape.labeldown, location=location.abovebar, "
                    + "color=color.new(color.orange, 0), text='BE', textcolor=color.white, size=size.tiny)\n");
            w.write("plotshape(isReview, title='No FVG target', style=shape.circle, location=location.belowbar, "
                    + "color=color.new(color.gray, 0), text='?', textcolor=color.gray, size=size.tiny)\n\n");

            w.write("var bool drawn = false\n");
            // Pine counts a for loop downwards when end < start, so an empty array needs the size guard.
            w.write("if barstate.islast and not drawn and array.size(entryT) > 0\n");
            w.write("    drawn := true\n");
            w.write("    for i = 0 to array.size(entryT) - 1\n");
            w.write("        l = array.get(entryT, i)\n");
            w.write("        r = array.get(result, i) == 0 ? time : array.get(exitT, i)\n");
            w.write("        e = array.get(entryPx, i)\n");
            w.write("        tp = array.get(tpPx, i)\n");
            w.write("        sl = array.get(slPx, i)\n");
            // TP is below entry on a short, so top and bottom are sorted rather than assumed.
            w.write("        box.new(l, math.max(tp, e), r, math.min(tp, e), xloc=xloc.bar_time, "
                    + "border_color=color.new(color.green, 60), bgcolor=color.new(color.green, 88))\n");
            w.write("        box.new(l, math.max(sl, e), r, math.min(sl, e), xloc=xloc.bar_time, "
                    + "border_color=color.new(color.red, 60), bgcolor=color.new(color.red, 88))\n");
            w.write("        if array.get(isLimit, i)\n");
            w.write("            line.new(array.get(alertT, i), e, l, e, xloc=xloc.bar_time, "
                    + "color=color.new(color.blue, 0), style=line.style_dotted, width=1)\n");
        }

        log.info("Exported {} of {} trades and {} no-target signals to {}",
                trades.size(), allTrades.size(), review.size(), outputPath);
    }

    private static long barStart(java.time.ZonedDateTime t, Timeframe timeframe) {
        long bar = timeframe.getDuration().toMillis();
        return Math.floorDiv(ms(t), bar) * bar;
    }

    private static long ms(java.time.ZonedDateTime t) {
        return t.toInstant().toEpochMilli();
    }
}
