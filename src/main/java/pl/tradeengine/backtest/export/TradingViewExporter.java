package pl.tradeengine.backtest.export;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class TradingViewExporter {

    public void export(List<AlertToSend> alerts, Path outputPath) throws IOException {
        Map<Direction, List<Long>> timestampsByDirection = alerts.stream()
                .collect(Collectors.groupingBy(
                        alert -> alert.getDirection() != null ? alert.getDirection() : Direction.LONG,
                        Collectors.mapping(
                                alert -> getTimestamp(alert).toInstant().toEpochMilli(),
                                Collectors.toList()
                        )
                ));

        List<Long> longTimestamps = timestampsByDirection.getOrDefault(Direction.LONG, List.of());
        List<Long> shortTimestamps = timestampsByDirection.getOrDefault(Direction.SHORT, List.of());

        StringBuilder pine = new StringBuilder();
        pine.append("//@version=5\n");
        pine.append("indicator(\"Backtest Results - GRINDER\", overlay=true)\n\n");

        if (!longTimestamps.isEmpty()) {
            pine.append("var longTimes = array.from(");
            pine.append(longTimestamps.stream().map(String::valueOf).collect(Collectors.joining(", ")));
            pine.append(")\n\n");

            pine.append("if array.includes(longTimes, time)\n");
            pine.append("    label.new(bar_index, low, \"🟢 BUY\", color=color.green, style=label.style_label_up, textcolor=color.white, size=size.small)\n\n");
        }

        if (!shortTimestamps.isEmpty()) {
            pine.append("var shortTimes = array.from(");
            pine.append(shortTimestamps.stream().map(String::valueOf).collect(Collectors.joining(", ")));
            pine.append(")\n\n");

            pine.append("if array.includes(shortTimes, time)\n");
            pine.append("    label.new(bar_index, high, \"🔴 SELL\", color=color.red, style=label.style_label_down, textcolor=color.white, size=size.small)\n");
        }

        Files.writeString(outputPath, pine.toString());
        log.info("✅ Exported {} alerts ({} LONG, {} SHORT) to: {}",
                alerts.size(), longTimestamps.size(), shortTimestamps.size(), outputPath);
    }

    private ZonedDateTime getTimestamp(AlertToSend alert) {
        // Zakładam że AlertToSend ma timestamp - jeśli nie, dostosuj
        return ZonedDateTime.now(); // TODO: Pobierz faktyczny timestamp z alertu
    }
}
