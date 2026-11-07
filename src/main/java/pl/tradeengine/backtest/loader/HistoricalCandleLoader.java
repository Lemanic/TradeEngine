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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class HistoricalCandleLoader {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Clock clock;

    public HistoricalCandleLoader() {
        this(Clock.systemUTC());
    }

    /** The clock decides which candles are still forming; injectable so a test can pin it. */
    public HistoricalCandleLoader(Clock clock) {
        this.clock = clock;
    }

    public List<PriceCandle> loadFromCsv(Path csvPath, Symbol symbol, Timeframe timeframe) throws IOException {
        List<PriceCandle> candles = new ArrayList<>();

        log.info("Loading candles from: {}", csvPath);

        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String line;

            String header = reader.readLine();
            if (header == null || !header.startsWith("time,open,high,low,close")) {
                throw new IOException("Invalid CSV header: " + header);
            }

            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    PriceCandle candle = parseLine(line, symbol, timeframe);
                    // A TradingView export always ends with the bar that is still forming. Replaying
                    // it as closed would let an unfinished high/low/close confirm a signal.
                    if (candle.closeTime().toInstant().isAfter(clock.instant())) {
                        log.info("Skipping unclosed candle {} {} opened at {}",
                                symbol.code(), timeframe, candle.openTime());
                        continue;
                    }
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

        // TradingView exports append Volume and one column per visible indicator; only OHLC matters.
        if (parts.length < 5) {
            throw new IllegalArgumentException("Expected at least 5 columns, got " + parts.length);
        }

        String dateStr = parts[0].trim();
        ZonedDateTime openTime;

        if (dateStr.matches("\\d+")) {
            // TradingView "UNIX timestamp" export: seconds since epoch.
            openTime = Instant.ofEpochSecond(Long.parseLong(dateStr)).atZone(ZoneOffset.UTC);
        } else try {
            openTime = ZonedDateTime.parse(dateStr);
        } catch (Exception e) {
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
