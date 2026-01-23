package pl.tradeengine.backtest.export;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class TradingViewExporter {

    private static final int MAX_ALERTS_TO_EXPORT = 400;

    public void export(List<AlertToSend> alerts, Path outputPath,
                       ZonedDateTime startDate, ZonedDateTime endDate) throws IOException {

        List<AlertToSend> filteredByDate = alerts.stream()
                .filter(a -> {
                    if (startDate != null && a.getTimestamp().isBefore(startDate)) {
                        return false;
                    }
                    if (endDate != null && a.getTimestamp().isAfter(endDate)) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        log.info("Date filter: {} to {} → {} alerts (from {} total)",
                startDate != null ? startDate : "beginning",
                endDate != null ? endDate : "end",
                filteredByDate.size(),
                alerts.size());

        List<AlertToSend> limitedAlerts = filteredByDate.size() > MAX_ALERTS_TO_EXPORT
                ? filteredByDate.subList(filteredByDate.size() - MAX_ALERTS_TO_EXPORT, filteredByDate.size())
                : filteredByDate;

        log.info("Exporting last {} out of {} alerts in date range to TradingView",
                limitedAlerts.size(), filteredByDate.size());

        exportToPineScript(limitedAlerts, outputPath);
    }

    public void export(List<AlertToSend> alerts, Path outputPath) throws IOException {
        export(alerts, outputPath, null, null);
    }

    private void exportToPineScript(List<AlertToSend> alerts, Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("//@version=5\n");
            writer.write("indicator('TradeEngine Backtest (Last " + alerts.size() + " Signals)', overlay=true)\n\n");

            List<Long> longTimestamps = alerts.stream()
                    .filter(a -> a.getDirection() == Direction.LONG)
                    .map(a -> a.getTimestamp().toInstant().toEpochMilli())
                    .collect(Collectors.toList());

            List<Long> shortTimestamps = alerts.stream()
                    .filter(a -> a.getDirection() == Direction.SHORT)
                    .map(a -> a.getTimestamp().toInstant().toEpochMilli())
                    .collect(Collectors.toList());

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

            writer.write("\n// Check if current bar matches signal time\n");
            writer.write("longCondition = array.includes(longTimes, time)\n");
            writer.write("shortCondition = array.includes(shortTimes, time)\n");

            writer.write("\n// Plot signals\n");
            writer.write("plotshape(longCondition, title='LONG', style=shape.triangleup, " +
                    "location=location.belowbar, color=color.new(color.green, 0), size=size.small)\n");
            writer.write("plotshape(shortCondition, title='SHORT', style=shape.triangledown, " +
                    "location=location.abovebar, color=color.new(color.red, 0), size=size.small)\n");

            writer.write("\n// Display info\n");
            writer.write(String.format("var label infoLabel = label.new(bar_index, high, " +
                            "'Showing %d signals\\n%d LONG / %d SHORT', " +
                            "style=label.style_label_left, color=color.new(color.blue, 80), textcolor=color.white)\n",
                    alerts.size(), longTimestamps.size(), shortTimestamps.size()));
            writer.write("if barstate.islast\n");
            writer.write("    label.set_xy(infoLabel, bar_index + 10, high)\n");
        }

        long longCount = alerts.stream().filter(a -> a.getDirection() == Direction.LONG).count();
        long shortCount = alerts.stream().filter(a -> a.getDirection() == Direction.SHORT).count();

        log.info("✅ Exported {} alerts ({} LONG, {} SHORT) to: {}",
                alerts.size(), longCount, shortCount, outputPath);
    }
}
