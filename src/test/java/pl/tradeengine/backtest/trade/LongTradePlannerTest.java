package pl.tradeengine.backtest.trade;

import org.junit.jupiter.api.Test;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.tradeengine.TestFixtures.candle;

class LongTradePlannerTest {

    private static final ZonedDateTime T0 = ZonedDateTime.parse("2025-03-03T00:00:00Z");

    /** SL from the last 3 bars, leg from the last 5, min 1.5R, H4 before H1. */
    private final LongTradePlanner planner = new LongTradePlanner(3, 5, 1.5, List.of(Timeframe.H4, Timeframe.H1));

    /** Leg high 130 on bar 1 (opens at T0+30m); lows of the last three bars 96, 95, 97; signal closes at 100. */
    private static final List<PriceCandle> CANDLES = List.of(
            candle(Timeframe.M30, T0, 120, 121, 118, 119),
            candle(Timeframe.M30, T0.plusMinutes(30), 119, 130, 110, 112),
            candle(Timeframe.M30, T0.plusMinutes(60), 112, 112, 96, 98),
            candle(Timeframe.M30, T0.plusMinutes(90), 98, 99, 95, 97),
            candle(Timeframe.M30, T0.plusMinutes(120), 97, 101, 97, 100));

    private static final ZonedDateTime IN_LEG = T0.plusHours(1);
    private static final ZonedDateTime BEFORE_LEG = T0;

    private SignalPlan plan(OpenFvg... zones) {
        return planner.plan(CANDLES, 4, List.of(zones));
    }

    @Test
    void stopLoss_isTheLowestLowOfTheLookback() {
        assertThat(plan(new OpenFvg(Timeframe.H4, 108, 112, IN_LEG)).stopLoss()).isEqualTo(95);
    }

    /** True ranges of bars 2-4 are 16, 4 and 4, so ATR(3) at the signal bar is 8. */
    private static LongTradePlanner withAtrFloor(double minStopAtr) {
        return new LongTradePlanner(3, 5, 1.5, List.of(Timeframe.H4, Timeframe.H1), minStopAtr, 3);
    }

    @Test
    void swingLowCloserThanTheAtrFloor_isPushedDownToIt() {
        SignalPlan plan = withAtrFloor(1.0).plan(CANDLES, 4, List.of(new OpenFvg(Timeframe.H4, 115, 120, IN_LEG)));

        assertThat(plan.stopLoss()).isEqualTo(92);
        assertThat(plan.rewardToRisk()).isEqualTo(2.5);
    }

    @Test
    void swingLowBeyondTheAtrFloor_staysWhereItIs() {
        SignalPlan plan = withAtrFloor(0.5).plan(CANDLES, 4, List.of(new OpenFvg(Timeframe.H4, 115, 120, IN_LEG)));

        assertThat(plan.stopLoss()).isEqualTo(95);
    }

    @Test
    void takeProfit_isTheTopOfTheHighestH4ZoneInTheLeg() {
        SignalPlan plan = plan(
                new OpenFvg(Timeframe.H4, 104, 106, IN_LEG),
                new OpenFvg(Timeframe.H4, 115, 120, IN_LEG),
                new OpenFvg(Timeframe.H1, 124, 126, IN_LEG));

        assertThat(plan.outcome()).isEqualTo(SignalOutcome.PLANNED);
        assertThat(plan.takeProfit()).isEqualTo(120);
        assertThat(plan.target().timeframe()).isEqualTo(Timeframe.H4);
        assertThat(plan.rewardToRisk()).isEqualTo(4.0);
    }

    @Test
    void nearestSelection_takesTheTopOfTheClosestH4ZoneAbovePrice() {
        LongTradePlanner nearest = new LongTradePlanner(3, 5, 1.5, List.of(Timeframe.H4, Timeframe.H1), 0, 0,
                TargetSelection.NEAREST);

        SignalPlan plan = nearest.plan(CANDLES, 4, List.of(
                new OpenFvg(Timeframe.H4, 115, 120, IN_LEG),
                new OpenFvg(Timeframe.H4, 104, 108, IN_LEG),
                new OpenFvg(Timeframe.H1, 101, 102, IN_LEG)));

        assertThat(plan.takeProfit()).isEqualTo(108);
        assertThat(plan.rewardToRisk()).isEqualTo(1.6);
    }

    @Test
    void fallsBackToH1_whenTheLegHasNoH4Zone() {
        SignalPlan plan = plan(
                new OpenFvg(Timeframe.H4, 124, 128, BEFORE_LEG),
                new OpenFvg(Timeframe.H1, 106, 110, IN_LEG));

        assertThat(plan.takeProfit()).isEqualTo(110);
        assertThat(plan.target().timeframe()).isEqualTo(Timeframe.H1);
    }

    @Test
    void zoneAlreadyTouched_stillCountsWhileItsTopIsAbovePrice() {
        assertThat(plan(new OpenFvg(Timeframe.H4, 99, 110, IN_LEG)).takeProfit()).isEqualTo(110);
    }

    @Test
    void zonesFromAnEarlierLeg_areIgnored() {
        SignalPlan plan = plan(new OpenFvg(Timeframe.H4, 124, 128, BEFORE_LEG));

        assertThat(plan.outcome()).isEqualTo(SignalOutcome.NO_TARGET);
        assertThat(plan.stopLoss()).isEqualTo(95);
    }

    @Test
    void rewardBelowTheThreshold_isRejected() {
        SignalPlan plan = plan(new OpenFvg(Timeframe.H4, 102, 105, IN_LEG));

        assertThat(plan.outcome()).isEqualTo(SignalOutcome.RR_TOO_LOW);
        assertThat(plan.rewardToRisk()).isEqualTo(1.0);
    }

    @Test
    void notEnoughHistory_isWarmup() {
        assertThat(planner.plan(CANDLES, 3, List.of()).outcome()).isEqualTo(SignalOutcome.WARMUP);
    }
}
