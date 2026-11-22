package pl.tradeengine.backtest.trade;

import org.junit.jupiter.api.Test;
import pl.tradeengine.domain.model.Timeframe;

import java.time.Duration;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.tradeengine.TestFixtures.candle;

class BullishFvgTrackerTest {

    private static final ZonedDateTime T0 = ZonedDateTime.parse("2025-03-03T00:00:00Z");

    /** Three rising M15 candles leaving a bullish gap [100, 103]. */
    private static BullishFvgTracker trackerWithBullishGap() {
        BullishFvgTracker tracker = new BullishFvgTracker(Duration.ofDays(7));
        tracker.onCandle(candle(Timeframe.M15, T0, 98, 100, 97, 99));
        tracker.onCandle(candle(Timeframe.M15, T0.plusMinutes(15), 99, 106, 99, 105));
        tracker.onCandle(candle(Timeframe.M15, T0.plusMinutes(30), 105, 108, 103, 107));
        return tracker;
    }

    @Test
    void bullishGap_betweenStopAndClose_isAnEntryZone() {
        assertThat(trackerWithBullishGap().highestBetween(95, 107))
                .contains(new OpenFvg(Timeframe.M15, 100, 103, T0.plusMinutes(45)));
    }

    @Test
    void zoneReachingBelowTheStop_doesNotQualify() {
        assertThat(trackerWithBullishGap().highestBetween(101, 107)).isEmpty();
    }

    @Test
    void touchingTheUpperEdge_keepsTheZone_reachingTheLowerEdge_fillsIt() {
        BullishFvgTracker tracker = trackerWithBullishGap();

        tracker.onCandle(candle(Timeframe.M15, T0.plusMinutes(45), 107, 107, 102, 104));
        assertThat(tracker.highestBetween(95, 107)).isPresent();

        tracker.onCandle(candle(Timeframe.M15, T0.plusMinutes(60), 104, 104, 100, 101));
        assertThat(tracker.highestBetween(95, 107)).isEmpty();
    }

    @Test
    void zonesOlderThanMaxAge_areDropped() {
        BullishFvgTracker tracker = trackerWithBullishGap();

        tracker.onCandle(candle(Timeframe.M15, T0.plusDays(8), 107, 108, 106, 107));

        assertThat(tracker.highestBetween(95, 107)).isEmpty();
    }
}
