package pl.tradeengine.backtest.structure;

import org.junit.jupiter.api.Test;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.tradeengine.TestFixtures.candle;

class MarketStructureTrackerTest {

    private static final ZonedDateTime T0 = ZonedDateTime.parse("2025-03-03T00:00:00Z");

    private final MarketStructureTracker tracker = new MarketStructureTracker();
    private int bar;

    private List<StructureEvent> next(double open, double high, double low, double close) {
        return tracker.onCandle(candle(Timeframe.H4, at(bar++), open, high, low, close));
    }

    private static ZonedDateTime at(int bar) {
        return T0.plusHours(4L * bar);
    }

    /** Seed bar (protected high 110), then a leg down to 85 leaving bearish FVGs [96, 100] and [90, 92]. */
    private void downLegTo85() {
        next(108, 110, 100, 101);
        next(101, 101, 92, 93);
        next(93, 96, 88, 89);
        next(89, 90, 85, 86);
    }

    /** {@link #downLegTo85()}, a new low at 84 that opens a gap [87, 88], then a pullback to 91 into it. */
    private List<StructureEvent> downLegAndPullbackIntoFvg() {
        downLegTo85();
        next(86, 87, 84, 85);
        return next(85, 91, 86.5, 90);
    }

    @Test
    void pullbackIntoAnFvgOfTheLeg_armsIt() {
        assertThat(downLegAndPullbackIntoFvg()).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(StructureEventType.FVG_TOUCH);
            assertThat(e.trend()).isEqualTo(Trend.DOWN);
            assertThat(e.zone().lower()).isEqualTo(87);
            assertThat(e.zone().upper()).isEqualTo(88);
            assertThat(e.zone().createdAt()).isEqualTo(at(2));
        });
        assertThat(tracker.protectedLevel()).isEqualTo(110);
    }

    @Test
    void pullbackStartingRightAfterTheGap_fromTheLegLow_stillArmsTheLeg() {
        downLegTo85();   // bar 3 makes the low and completes [90, 92]

        assertThat(next(86, 91, 86.5, 90)).extracting(StructureEvent::type)
                .containsExactly(StructureEventType.FVG_TOUCH);
        assertThat(next(90, 90, 84, 86)).extracting(StructureEvent::type)
                .containsExactly(StructureEventType.SWEEP);
        assertThat(tracker.protectedLevel()).isEqualTo(91);
    }

    @Test
    void wickToTheLegLow_afterTheTouch_isASweep_andMovesTheProtectedHighToThePullback() {
        downLegAndPullbackIntoFvg();

        List<StructureEvent> events = next(90, 90, 83.5, 86);

        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(StructureEventType.SWEEP);
            assertThat(e.level()).isEqualTo(84);
            assertThat(e.levelTime()).isEqualTo(at(4));
        });
        assertThat(tracker.protectedLevel()).isEqualTo(91);
        assertThat(tracker.trend()).isEmpty();
    }

    @Test
    void closeBelowTheLegLow_afterTheTouch_isABos() {
        downLegAndPullbackIntoFvg();

        List<StructureEvent> events = next(90, 90, 83, 83.5);

        assertThat(events).extracting(StructureEvent::type).containsExactly(StructureEventType.BOS);
        assertThat(tracker.protectedLevel()).isEqualTo(91);
        assertThat(tracker.trend()).contains(Trend.DOWN);
    }

    @Test
    void closeBelowASweptLow_later_confirmsTheBos() {
        downLegAndPullbackIntoFvg();
        next(90, 90, 83.5, 86);

        List<StructureEvent> events = next(86, 87, 83, 83.8);

        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(StructureEventType.BOS);
            assertThat(e.level()).isEqualTo(84);
        });
        assertThat(tracker.trend()).contains(Trend.DOWN);
    }

    @Test
    void newLowWithoutAnFvgTouch_onlyExtendsTheLeg() {
        downLegTo85();
        next(86, 89, 86, 88);

        assertThat(next(88, 88, 84, 85)).isEmpty();
        assertThat(tracker.protectedLevel()).isEqualTo(110);
    }

    @Test
    void closeAboveTheProtectedHigh_isAnMss_andTheLegLowBecomesTheProtectedLow() {
        downLegAndPullbackIntoFvg();
        next(90, 90, 83.5, 86);
        next(86, 87, 83, 83.8);

        List<StructureEvent> events = next(84, 95, 84, 92);

        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(StructureEventType.MSS);
            assertThat(e.trend()).isEqualTo(Trend.UP);
            assertThat(e.level()).isEqualTo(91);
            assertThat(e.levelTime()).isEqualTo(at(5));
        });
        assertThat(tracker.trend()).contains(Trend.UP);
        assertThat(tracker.protectedLevel()).isEqualTo(83);
    }

    @Test
    void wickAboveTheProtectedHigh_closingBelowIt_isNoMss_andMovesTheProtectedHighToTheWick() {
        downLegAndPullbackIntoFvg();
        next(90, 90, 83, 83.5);  // BOS, protected high 91

        List<StructureEvent> events = next(84, 93, 84, 90);

        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(StructureEventType.PROTECTED_SWEEP);
            assertThat(e.level()).isEqualTo(91);
            assertThat(e.levelTime()).isEqualTo(at(5));
        });
        assertThat(tracker.trend()).contains(Trend.DOWN);
        assertThat(tracker.protectedLevel()).isEqualTo(93);
    }

    @Test
    void afterAProtectedSweep_onlyACloseAboveTheWickIsAnMss() {
        downLegAndPullbackIntoFvg();
        next(90, 90, 83, 83.5);
        next(84, 93, 84, 90);    // protected high moves to 93

        assertThat(next(90, 92.5, 89, 92)).isEmpty();

        assertThat(next(92, 95, 91, 94)).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(StructureEventType.MSS);
            assertThat(e.level()).isEqualTo(93);
            assertThat(e.levelTime()).isEqualTo(at(7));
        });
        assertThat(tracker.trend()).contains(Trend.UP);
    }

    /**
     * Downtrend after a BOS (protected high 91, leg low 83), then a counter-leg up to 89 leaving bullish
     * FVG [84, 86], and a pullback to 85 into it.
     */
    private List<StructureEvent> counterLegPullbackIntoItsFvg(MarketStructureTracker counter) {
        List<StructureEvent> events = new java.util.ArrayList<>();
        for (double[] c : new double[][]{
                {108, 110, 100, 101}, {101, 101, 92, 93}, {93, 96, 88, 89}, {89, 90, 85, 86},
                {86, 87, 84, 85}, {85, 91, 86.5, 90}, {90, 90, 83, 83.5},   // BOS, protected high 91, leg low 83
                {83.5, 84, 83.2, 84}, {84, 87, 83.8, 86.5}, {86.5, 89, 86, 88.5},  // up to 89, FVG [84, 86]
                {88.5, 88.6, 85, 85.5}}) {   // pullback to 85
            events = counter.onCandle(candle(Timeframe.H4, at(bar++), c[0], c[1], c[2], c[3]));
        }
        return events;
    }

    @Test
    void counterLegVariant_pullbackIntoTheCounterLegsFvg_isReportedAsTheCounterTrendsTouch() {
        MarketStructureTracker counter = new MarketStructureTracker(true);

        assertThat(counterLegPullbackIntoItsFvg(counter)).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(StructureEventType.FVG_TOUCH);
            assertThat(e.trend()).isEqualTo(Trend.UP);
            assertThat(e.zone().lower()).isEqualTo(84);
            assertThat(e.zone().upper()).isEqualTo(86);
        });
        assertThat(counter.trend()).contains(Trend.DOWN);
    }

    @Test
    void counterLegVariant_closeAboveTheCounterLegHigh_isAnMss_belowTheProtectedHigh() {
        MarketStructureTracker counter = new MarketStructureTracker(true);
        counterLegPullbackIntoItsFvg(counter);

        List<StructureEvent> events = counter.onCandle(candle(Timeframe.H4, at(bar++), 85.5, 90, 85.4, 89.5));

        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(StructureEventType.MSS);
            assertThat(e.trend()).isEqualTo(Trend.UP);
            assertThat(e.level()).isEqualTo(89);
        });
        assertThat(counter.trend()).contains(Trend.UP);
        assertThat(counter.protectedLevel()).isEqualTo(85);
    }

    @Test
    void counterLegVariant_wickAboveTheCounterLegHigh_onlyMovesIt() {
        MarketStructureTracker counter = new MarketStructureTracker(true);
        counterLegPullbackIntoItsFvg(counter);

        assertThat(counter.onCandle(candle(Timeframe.H4, at(bar++), 85.5, 89.5, 85.4, 88))).isEmpty();
        assertThat(counter.onCandle(candle(Timeframe.H4, at(bar++), 88, 89.4, 87, 89.2))).isEmpty();

        assertThat(counter.onCandle(candle(Timeframe.H4, at(bar++), 89.2, 90, 89, 89.8)))
                .extracting(StructureEvent::type).containsExactly(StructureEventType.MSS);
        assertThat(counter.protectedLevel()).isEqualTo(85);
    }

    @Test
    void baseVariant_ignoresTheCounterLeg() {
        counterLegPullbackIntoItsFvg(tracker);

        assertThat(next(85.5, 90, 85.4, 89.5)).isEmpty();
        assertThat(tracker.trend()).contains(Trend.DOWN);
        assertThat(tracker.protectedLevel()).isEqualTo(91);
    }

    @Test
    void afterAnMss_theUptrendMirrorsTheRules() {
        downLegAndPullbackIntoFvg();
        next(90, 90, 83.5, 86);
        next(86, 87, 83, 83.8);
        next(84, 95, 84, 92);
        next(92, 100, 91, 99);   // new high 100, leaves bullish FVG [87, 91]
        next(99, 102, 95, 101);

        assertThat(next(101, 101, 90, 93)).extracting(StructureEvent::type)
                .containsExactly(StructureEventType.FVG_TOUCH);

        List<StructureEvent> events = next(93, 103, 93, 102.5);

        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(StructureEventType.BOS);
            assertThat(e.trend()).isEqualTo(Trend.UP);
            assertThat(e.level()).isEqualTo(102);
        });
        assertThat(tracker.protectedLevel()).isEqualTo(90);
    }
}
