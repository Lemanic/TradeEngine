package pl.tradeengine.backtest.trade;

import org.junit.jupiter.api.Test;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.tradeengine.TestFixtures.candle;

class HtfFvgTrackerTest {

    private static final ZonedDateTime T0 = ZonedDateTime.parse("2025-03-03T00:00:00Z");

    /** Three falling H4 candles leaving a bearish gap [100, 105]. */
    private static HtfFvgTracker trackerWithBearishGap() {
        HtfFvgTracker tracker = new HtfFvgTracker();
        tracker.onHtfCandle(candle(Timeframe.H4, T0, 112, 112, 105, 106));
        tracker.onHtfCandle(candle(Timeframe.H4, T0.plusHours(4), 106, 106, 98, 99));
        tracker.onHtfCandle(candle(Timeframe.H4, T0.plusHours(8), 99, 100, 95, 96));
        return tracker;
    }

    @Test
    void bearishGap_isKnownFromTheCloseOfItsThirdCandle() {
        assertThat(trackerWithBearishGap().openZones())
                .containsExactly(new OpenFvg(Timeframe.H4, 100, 105, T0.plusHours(12)));
    }

    @Test
    void touchingTheLowerEdge_keepsTheZone() {
        HtfFvgTracker tracker = trackerWithBearishGap();

        tracker.onPrice(104.9);

        assertThat(tracker.openZones()).hasSize(1);
    }

    @Test
    void reachingTheUpperEdge_fillsTheZone() {
        HtfFvgTracker tracker = trackerWithBearishGap();

        tracker.onPrice(105);

        assertThat(tracker.openZones()).isEmpty();
    }

    @Test
    void bullishGap_isNotATargetForALong() {
        HtfFvgTracker tracker = new HtfFvgTracker();
        tracker.onHtfCandle(candle(Timeframe.H1, T0, 95, 100, 95, 99));
        tracker.onHtfCandle(candle(Timeframe.H1, T0.plusHours(1), 99, 108, 99, 107));
        tracker.onHtfCandle(candle(Timeframe.H1, T0.plusHours(2), 107, 110, 103, 109));

        assertThat(tracker.openZones()).isEmpty();
    }
}
