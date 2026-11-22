package pl.tradeengine.backtest.trade;

import org.junit.jupiter.api.Test;
import pl.tradeengine.domain.model.Timeframe;

import java.time.Duration;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.tradeengine.TestFixtures.candle;

class InversionFvgTrackerTest {

    private static final ZonedDateTime T0 = ZonedDateTime.parse("2025-03-03T00:00:00Z");

    /** Three falling M15 candles leaving a bearish gap [100, 103]. */
    private static InversionFvgTracker trackerWithBearishGap() {
        InversionFvgTracker tracker = new InversionFvgTracker(Duration.ofDays(7));
        tracker.onCandle(candle(Timeframe.M15, T0, 106, 107, 103, 104));
        tracker.onCandle(candle(Timeframe.M15, T0.plusMinutes(15), 104, 104, 96, 97));
        tracker.onCandle(candle(Timeframe.M15, T0.plusMinutes(30), 97, 100, 95, 96));
        return tracker;
    }

    @Test
    void bearishGapNotClosedAbove_isNoEntryZoneYet() {
        InversionFvgTracker tracker = trackerWithBearishGap();

        tracker.onCandle(candle(Timeframe.M15, T0.plusMinutes(45), 96, 104, 96, 102));

        assertThat(tracker.highestBetween(90, 110)).isEmpty();
    }

    @Test
    void closeAboveTheUpperEdge_invertsTheGap() {
        InversionFvgTracker tracker = trackerWithBearishGap();

        tracker.onCandle(candle(Timeframe.M15, T0.plusMinutes(45), 96, 106, 96, 105));

        assertThat(tracker.highestBetween(90, 110))
                .contains(new OpenFvg(Timeframe.M15, 100, 103, T0.plusMinutes(60)));
    }

    @Test
    void closeBelowTheLowerEdge_invalidatesTheInversion_aWickDoesNot() {
        InversionFvgTracker tracker = trackerWithBearishGap();
        tracker.onCandle(candle(Timeframe.M15, T0.plusMinutes(45), 96, 106, 96, 105));

        tracker.onCandle(candle(Timeframe.M15, T0.plusMinutes(60), 105, 105, 98, 101));
        assertThat(tracker.highestBetween(90, 110)).isPresent();

        tracker.onCandle(candle(Timeframe.M15, T0.plusMinutes(75), 101, 101, 97, 99));
        assertThat(tracker.highestBetween(90, 110)).isEmpty();
    }

    @Test
    void zoneReachingBelowTheStop_doesNotQualify() {
        InversionFvgTracker tracker = trackerWithBearishGap();
        tracker.onCandle(candle(Timeframe.M15, T0.plusMinutes(45), 96, 106, 96, 105));

        assertThat(tracker.highestBetween(101, 110)).isEmpty();
    }
}
