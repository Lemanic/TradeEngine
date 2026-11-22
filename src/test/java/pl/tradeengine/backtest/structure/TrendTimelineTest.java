package pl.tradeengine.backtest.structure;

import org.junit.jupiter.api.Test;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.tradeengine.TestFixtures.candle;

class TrendTimelineTest {

    private static final ZonedDateTime T0 = ZonedDateTime.parse("2025-03-03T00:00:00Z");

    /** A D1 leg down, a pullback into its FVG and a close below the leg low: BOS on day 6. */
    private static TrendTimeline downtrendConfirmedOnDay6() {
        double[][] ohlc = {
                {108, 110, 100, 101}, {101, 101, 92, 93}, {93, 96, 88, 89}, {89, 90, 85, 86},
                {86, 87, 84, 85}, {85, 91, 86.5, 90}, {90, 90, 83, 83.5}};
        List<PriceCandle> candles = new ArrayList<>();
        for (int i = 0; i < ohlc.length; i++) {
            double[] c = ohlc[i];
            candles.add(candle(Timeframe.D1, T0.plusDays(i), c[0], c[1], c[2], c[3]));
        }
        return new TrendTimeline(candles, new MarketStructureTracker());
    }

    @Test
    void duringTheDayThatConfirmsTheTrend_theTrendIsNotYetKnown() {
        assertThat(downtrendConfirmedOnDay6().at(T0.plusDays(6).plusHours(23))).isEmpty();
    }

    @Test
    void fromTheCloseOfThatDay_theTrendIsKnown() {
        TrendTimeline timeline = downtrendConfirmedOnDay6();

        assertThat(timeline.at(T0.plusDays(7))).contains(Trend.DOWN);
        assertThat(timeline.at(T0.plusDays(30))).contains(Trend.DOWN);
    }

    @Test
    void beforeTheFirstCandleCloses_thereIsNoTrend() {
        assertThat(downtrendConfirmedOnDay6().at(T0.plusHours(12))).isEmpty();
    }
}
