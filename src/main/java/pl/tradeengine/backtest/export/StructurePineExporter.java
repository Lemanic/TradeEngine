package pl.tradeengine.backtest.export;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.backtest.structure.StructureEvent;
import pl.tradeengine.backtest.structure.Trend;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pine Script overlay of {@code MarketStructureTracker} output, for checking it by eye:
 * a step line at the protected level (green in an uptrend, red in a downtrend, grey before the trend is
 * known), BOS/MSS/sweep lines from the broken extreme to the bar that broke it, a dotted "no MSS" line
 * where a wick swept the protected level and moved it, and a box over each FVG
 * whose touch armed a leg.
 *
 * <p>Everything is drawn by bar time, so the chart must be on the tracker's timeframe. TradingView keeps
 * only the newest 500 lines, labels and boxes, so on a long range the oldest drawings disappear.
 */
@Slf4j
public class StructurePineExporter {

    /** The protected level from {@code time} on; {@code trend} is null until the tracker knows one. */
    public record LevelChange(ZonedDateTime time, double level, Trend trend) {
    }

    private static final int VALUES_PER_LINE = 100;
    private static final int CHUNKS_PER_BLOCK = 10;

    public void export(List<StructureEvent> events, List<LevelChange> levels, String title, Path outputPath)
            throws IOException {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        for (LevelChange l : levels) {
            add(columns, "lT", String.valueOf(ms(l.time())));
            add(columns, "lV", price(l.level()));
            add(columns, "lTr", String.valueOf(l.trend() == null ? 0 : l.trend() == Trend.UP ? 1 : -1));
        }
        for (StructureEvent e : events) {
            add(columns, "eT", String.valueOf(ms(e.time())));
            add(columns, "eK", String.valueOf(e.type().ordinal()));
            add(columns, "eUp", String.valueOf(e.trend() == Trend.UP));
            add(columns, "eV", price(e.level()));
            add(columns, "eFrom", String.valueOf(ms(e.levelTime())));
            add(columns, "zLo", price(e.zone() == null ? 0 : e.zone().lower()));
            add(columns, "zHi", price(e.zone() == null ? 0 : e.zone().upper()));
        }

        try (BufferedWriter w = Files.newBufferedWriter(outputPath)) {
            w.write("//@version=6\n");
            w.write("indicator('" + title + "', overlay=true, "
                    + "max_boxes_count=500, max_labels_count=500, max_lines_count=500)\n\n");
            w.write("showTouch = input.bool(true, 'FVG touch boxes')\n");
            w.write("showSweep = input.bool(true, 'Sweeps (wick only)')\n");
            w.write("showBg = input.bool(true, 'Trend background')\n\n");

            w.write("var lT = array.new_int()\n");
            w.write("var lV = array.new_float()\n");
            w.write("var lTr = array.new_int()   // 1 = up, -1 = down, 0 = not known yet\n");
            w.write("var eT = array.new_int()\n");
            w.write("var eK = array.new_int()    // 0 = FVG touch, 1 = sweep, 2 = BOS, 3 = MSS, 4 = protected level swept\n");
            w.write("var eUp = array.new_bool()\n");
            w.write("var eV = array.new_float()\n");
            w.write("var eFrom = array.new_int()\n");
            w.write("var zLo = array.new_float()\n");
            w.write("var zHi = array.new_float()\n");
            // One array.from per chunk instead of one array.push per value keeps the script far under
            // TradingView's length limit; the chunks are concatenated once, on the first bar. Pine also
            // rejects one long if-block ("the if statement is too long"), so the chunks go into several.
            List<String> chunks = new ArrayList<>();
            for (Map.Entry<String, List<String>> column : columns.entrySet()) {
                List<String> values = column.getValue();
                for (int i = 0; i < values.size(); i += VALUES_PER_LINE) {
                    chunks.add("array.concat(" + column.getKey() + ", array.from("
                            + String.join(",", values.subList(i, Math.min(i + VALUES_PER_LINE, values.size()))) + "))");
                }
            }
            for (int i = 0; i < chunks.size(); i++) {
                if (i % CHUNKS_PER_BLOCK == 0) {
                    w.write("if barstate.isfirst\n");
                }
                w.write("    " + chunks.get(i) + "\n");
            }

            // Both arrays are sorted by time; a cursor walks each, so several entries on one bar all apply.
            // The cursors rely on v6 evaluating `and` lazily: v5 would read past the end of the array.
            w.write("\nvar float prot = na\n");
            w.write("var int tr = 0\n");
            w.write("var int li = 0\n");
            w.write("while li < array.size(lT) and array.get(lT, li) <= time\n");
            w.write("    prot := array.get(lV, li)\n");
            w.write("    tr := array.get(lTr, li)\n");
            w.write("    li += 1\n");
            w.write("trCol = tr == 1 ? color.green : tr == -1 ? color.red : color.gray\n");
            w.write("plot(prot, 'Protected level', color=trCol, style=plot.style_stepline, linewidth=2)\n");
            w.write("bgcolor(showBg and tr != 0 ? color.new(trCol, 93) : na, title='Trend')\n\n");

            w.write("var int ei = 0\n");
            w.write("while ei < array.size(eT) and array.get(eT, ei) <= time\n");
            w.write("    if array.get(eT, ei) == time\n");
            w.write("        k = array.get(eK, ei)\n");
            w.write("        up = array.get(eUp, ei)\n");
            w.write("        v = array.get(eV, ei)\n");
            w.write("        f = array.get(eFrom, ei)\n");
            w.write("        col = up ? color.green : color.red\n");
            // A downtrend breaks lows, so its labels sit below the level; an uptrend's above it.
            w.write("        st = up ? label.style_label_down : label.style_label_up\n");
            w.write("        if k == 0 and showTouch\n");
            w.write("            box.new(f, array.get(zHi, ei), time, array.get(zLo, ei), xloc=xloc.bar_time, "
                    + "border_color=color.new(col, 40), bgcolor=color.new(col, 80))\n");
            w.write("        if k == 1 and showSweep\n");
            w.write("            line.new(f, v, time, v, xloc=xloc.bar_time, color=color.gray, style=line.style_dotted)\n");
            w.write("            label.new(time, v, 'sweep', xloc=xloc.bar_time, style=st, "
                    + "color=color.new(color.gray, 100), textcolor=color.gray, size=size.small)\n");
            w.write("        if k == 2\n");
            w.write("            line.new(f, v, time, v, xloc=xloc.bar_time, color=col, width=1)\n");
            w.write("            label.new(time, v, 'BOS', xloc=xloc.bar_time, style=st, "
                    + "color=color.new(col, 100), textcolor=col, size=size.small)\n");
            w.write("        if k == 3\n");
            w.write("            line.new(f, v, time, v, xloc=xloc.bar_time, color=col, style=line.style_dashed, width=2)\n");
            w.write("            label.new(time, v, 'MSS', xloc=xloc.bar_time, style=st, "
                    + "color=col, textcolor=color.white, size=size.small)\n");
            // A swept protected level lies against the trend: a protected low below price, a protected high above it.
            w.write("        if k == 4 and showSweep\n");
            w.write("            line.new(f, v, time, v, xloc=xloc.bar_time, color=col, style=line.style_dotted)\n");
            w.write("            label.new(time, v, 'no MSS', xloc=xloc.bar_time, "
                    + "style=up ? label.style_label_up : label.style_label_down, "
                    + "color=color.new(col, 100), textcolor=col, size=size.small)\n");
            w.write("    ei += 1\n");
        }

        log.info("Exported {} structure events and {} protected-level changes to {}",
                events.size(), levels.size(), outputPath);
    }

    private static void add(Map<String, List<String>> columns, String name, String value) {
        columns.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
    }

    private static String price(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static long ms(ZonedDateTime t) {
        return t.toInstant().toEpochMilli();
    }
}
