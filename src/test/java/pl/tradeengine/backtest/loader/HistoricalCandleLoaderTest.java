package pl.tradeengine.backtest.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalCandleLoaderTest {

    private static final Symbol TAO = new Symbol("TAOUSDT");

    @TempDir
    Path dir;

    private List<PriceCandle> load(String csv, Timeframe tf, String now) throws Exception {
        Path file = dir.resolve("candles.csv");
        Files.writeString(file, csv);
        Clock clock = Clock.fixed(Instant.parse(now), ZoneOffset.UTC);
        return new HistoricalCandleLoader(clock).loadFromCsv(file, TAO, tf);
    }

    @Test
    void readsATradingViewExport_withUnixSecondsAndExtraColumns() throws Exception {
        List<PriceCandle> candles = load("""
                time,open,high,low,close,Volume
                1712815200,500.1,510,495,505,1234
                """, Timeframe.H1, "2030-01-01T00:00:00Z");

        assertThat(candles).singleElement().satisfies(c -> {
            assertThat(c.openTime()).isEqualTo(ZonedDateTime.parse("2024-04-11T06:00:00Z"));
            assertThat(c.closeTime()).isEqualTo(ZonedDateTime.parse("2024-04-11T07:00:00Z"));
            assertThat(c.close()).isEqualByComparingTo("505");
        });
    }

    @Test
    void skipsTheCandleThatIsStillForming() throws Exception {
        // H4 bars at 04:00 and 08:00 UTC; at 10:00 the second one has two hours left.
        List<PriceCandle> candles = load("""
                time,open,high,low,close
                2026-11-07T04:00:00Z,1,2,1,2
                2026-11-07T08:00:00Z,2,3,2,3
                """, Timeframe.H4, "2026-11-07T10:00:00Z");

        assertThat(candles).extracting(PriceCandle::openTime)
                .containsExactly(ZonedDateTime.parse("2026-11-07T04:00:00Z"));
    }

    @Test
    void keepsACandleWhoseCloseIsExactlyNow() throws Exception {
        List<PriceCandle> candles = load("""
                time,open,high,low,close
                2026-11-07T08:00:00Z,2,3,2,3
                """, Timeframe.H4, "2026-11-07T12:00:00Z");

        assertThat(candles).hasSize(1);
    }
}
