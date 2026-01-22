package pl.tradeengine.backtest.export;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class TradingViewExporter {

    private static final int MAX_ALERTS_TO_EXPORT = 100;  // ← Limit

    public void export(List<AlertToSend> alerts, Path outputPath) throws IOException {
        // Ogranicz do ostatnich N alertów
        List<AlertToSend> limitedAlerts = alerts.size() > MAX_ALERTS_TO_EXPORT
                ? alerts.subList(alerts.size() - MAX_ALERTS_TO_EXPORT, alerts.size())
                : alerts;

        log.info("Exporting last {} out of {} total alerts to TradingView",
                limitedAlerts.size(), alerts.size());

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            // Header
            writer.write("//@version=5\n");
            writer.write("indicator('TradeEngine Backtest (Last " + limitedAlerts.size() + " Signals)', overlay=true)\n\n");

            // Collect timestamps
            List<Long> longTimestamps = limitedAlerts.stream()
                    .filter(a -> a.getDirection() == Direction.LONG)
                    .map(a -> a.getTimestamp().toInstant().toEpochMilli())
                    .collect(Collectors.toList());

            List<Long> shortTimestamps = limitedAlerts.stream()
                    .filter(a -> a.getDirection() == Direction.SHORT)
                    .map(a -> a.getTimestamp().toInstant().toEpochMilli())
                    .collect(Collectors.toList());

            // Create arrays - split into chunks if needed
            writer.write("// LONG timestamps\n");
            writer.write("var longTimes = array.new_int()\n");
            if (!longTimestamps.isEmpty()) {
                writer.write("if barstate.isfirst\n");
                for (Long ts : longTimestamps) {
                    writer.write("    array.push(longTimes, " + ts + ")\n");
                }
            }

            writer.write("\n// SHORT timestamps\n");
            writer.write("var shortTimes = array.new_int()\n");
            if (!shortTimestamps.isEmpty()) {
                writer.write("if barstate.isfirst\n");
                for (Long ts : shortTimestamps) {
                    writer.write("    array.push(shortTimes, " + ts + ")\n");
                }
            }

            // Check conditions
            writer.write("\n// Check if current bar matches signal time\n");
            writer.write("longCondition = array.includes(longTimes, time)\n");
            writer.write("shortCondition = array.includes(shortTimes, time)\n");

            // Plot shapes
            writer.write("\n// Plot signals\n");
            writer.write("plotshape(longCondition, title='LONG', style=shape.triangleup, " +
                    "location=location.belowbar, color=color.new(color.green, 0), size=size.small)\n");
            writer.write("plotshape(shortCondition, title='SHORT', style=shape.triangledown, " +
                    "location=location.abovebar, color=color.new(color.red, 0), size=size.small)\n");

            // Stats label
            writer.write("\n// Display info\n");
            writer.write(String.format("var label infoLabel = label.new(bar_index, high, " +
                            "'Last %d signals\\n%d LONG / %d SHORT\\n(Total: %d)', " +
                            "style=label.style_label_left, color=color.new(color.blue, 80), textcolor=color.white)\n",
                    limitedAlerts.size(), longTimestamps.size(), shortTimestamps.size(), alerts.size()));
            writer.write("if barstate.islast\n");
            writer.write("    label.set_xy(infoLabel, bar_index + 10, high)\n");
        }

        long longCount = limitedAlerts.stream().filter(a -> a.getDirection() == Direction.LONG).count();
        long shortCount = limitedAlerts.stream().filter(a -> a.getDirection() == Direction.SHORT).count();

        log.info("✅ Exported {} alerts ({} LONG, {} SHORT) to: {}",
                limitedAlerts.size(), longCount, shortCount, outputPath);
    }
}
