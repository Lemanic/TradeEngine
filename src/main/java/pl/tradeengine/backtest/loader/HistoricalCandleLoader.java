package pl.tradeengine.backtest.loader;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class HistoricalCandleLoader {

    // Formatter dla formatu: YYYY-MM-DD (tylko data, bez czasu)
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public List<PriceCandle> loadFromCsv(Path csvPath, Symbol symbol, Timeframe timeframe) throws IOException {
        List<PriceCandle> candles = new ArrayList<>();

        log.info("Loading candles from: {}", csvPath);

        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String line;

            // Skip header
            String header = reader.readLine();
            if (header == null || !header.startsWith("time,open,high,low,close")) {
                throw new IOException("Invalid CSV header: " + header);
            }

            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    PriceCandle candle = parseLine(line, symbol, timeframe);
                    candles.add(candle);
                } catch (Exception e) {
                    log.warn("Failed to parse line {}: {} - Error: {}", lineNumber, line, e.getMessage());
                }
            }
        }

        log.info("Loaded {} candles for {} {}", candles.size(), symbol.code(), timeframe);
        return candles;
    }

    private PriceCandle parseLine(String line, Symbol symbol, Timeframe timeframe) {
        String[] parts = line.split(",");

        if (parts.length != 5) {
            throw new IllegalArgumentException("Expected 5 columns, got " + parts.length);
        }

        // Parse date (format: 2020-11-30)
        String dateStr = parts[0].trim();
        ZonedDateTime openTime;

        try {
            // Próba parsowania jako ISO datetime (2016-01-01T01:00:00+01:00)
            openTime = ZonedDateTime.parse(dateStr);
        } catch (Exception e) {
            // Jeśli się nie uda, parsuj jako samą datę (2020-11-30)
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
            openTime = date.atStartOfDay(ZoneOffset.UTC);
        }

        ZonedDateTime closeTime = openTime.plus(timeframe.getDuration());

        BigDecimal open = new BigDecimal(parts[1]);
        BigDecimal high = new BigDecimal(parts[2]);
        BigDecimal low = new BigDecimal(parts[3]);
        BigDecimal close = new BigDecimal(parts[4]);

        return new PriceCandle(symbol, timeframe, openTime, closeTime, open, high, low, close);
    }
}
