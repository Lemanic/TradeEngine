package pl.tradeengine.backtest.trade;

import org.junit.jupiter.api.Test;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.tradeengine.TestFixtures.candle;

class OpenPositionTest {

    private static final ZonedDateTime T0 = ZonedDateTime.parse("2025-03-03T00:00:00Z");

    /** SL 95, TP 120, planned on a close of 100. */
    private static final SignalPlan PLAN = new SignalPlan(T0, SignalOutcome.PLANNED, 100, 95.0, 120.0,
            new OpenFvg(Timeframe.H4, 115, 120, T0.minusHours(4)), 4.0, T0.minusHours(8), Direction.LONG);

    private static OpenPosition enteredAt100() {
        return OpenPosition.enter(PLAN, candle(Timeframe.M30, T0, 100, 101, 99, 100), 100).orElseThrow();
    }

    @Test
    void stopLoss_losesExactlyTheRiskedAmount() {
        Trade trade = enteredAt100().exitOn(candle(Timeframe.M30, T0.plusMinutes(30), 99, 99, 94, 96), 10_000).orElseThrow();

        assertThat(trade.exitReason()).isEqualTo(ExitReason.SL);
        assertThat(trade.pnl()).isEqualTo(-100);
        assertThat(trade.rMultiple()).isEqualTo(-1);
        assertThat(trade.equityAfter()).isEqualTo(9_900);
    }

    @Test
    void takeProfit_paysTheDistanceInR() {
        Trade trade = enteredAt100().exitOn(candle(Timeframe.M30, T0.plusMinutes(30), 110, 121, 109, 118), 10_000).orElseThrow();

        assertThat(trade.exitReason()).isEqualTo(ExitReason.TP);
        assertThat(trade.exitPrice()).isEqualTo(120);
        assertThat(trade.rMultiple()).isEqualTo(4);
        assertThat(trade.pnl()).isEqualTo(400);
    }

    @Test
    void barReachingBothLevels_countsAsStopLoss() {
        Trade trade = enteredAt100().exitOn(candle(Timeframe.M30, T0.plusMinutes(30), 100, 125, 90, 110), 10_000).orElseThrow();

        assertThat(trade.exitReason()).isEqualTo(ExitReason.SL);
    }

    @Test
    void gapThroughStopLoss_fillsAtTheOpen() {
        Trade trade = enteredAt100().exitOn(candle(Timeframe.M30, T0.plusMinutes(30), 90, 92, 88, 91), 10_000).orElseThrow();

        assertThat(trade.exitPrice()).isEqualTo(90);
        assertThat(trade.rMultiple()).isEqualTo(-2);
    }

    @Test
    void barInsideTheRange_keepsThePositionOpen() {
        assertThat(enteredAt100().exitOn(candle(Timeframe.M30, T0.plusMinutes(30), 100, 110, 96, 105), 10_000)).isEmpty();
    }

    /** Risk 5, so +1R is 105. */
    private static OpenPosition enteredAt100WithBreakEvenAt1R() {
        return OpenPosition.enter(PLAN, candle(Timeframe.M30, T0, 100, 101, 99, 100), 100, 1.0).orElseThrow();
    }

    @Test
    void afterReaching1R_aPullbackToEntryClosesAtZero() {
        OpenPosition position = enteredAt100WithBreakEvenAt1R();
        assertThat(position.exitOn(candle(Timeframe.M30, T0.plusMinutes(30), 101, 106, 101, 104), 10_000)).isEmpty();

        Trade trade = position.exitOn(candle(Timeframe.M30, T0.plusMinutes(60), 104, 104, 99, 100), 10_000).orElseThrow();

        assertThat(trade.exitReason()).isEqualTo(ExitReason.BE);
        assertThat(trade.exitPrice()).isEqualTo(100);
        assertThat(trade.pnl()).isZero();
        assertThat(trade.stopLoss()).isEqualTo(95);
    }

    @Test
    void barThatReaches1R_isStillJudgedAgainstTheOriginalStop() {
        assertThat(enteredAt100WithBreakEvenAt1R()
                .exitOn(candle(Timeframe.M30, T0.plusMinutes(30), 101, 106, 99, 104), 10_000)).isEmpty();
    }

    @Test
    void withoutBreakEven_aPullbackToEntryKeepsThePosition() {
        OpenPosition position = enteredAt100();
        position.exitOn(candle(Timeframe.M30, T0.plusMinutes(30), 101, 106, 101, 104), 10_000);

        assertThat(position.exitOn(candle(Timeframe.M30, T0.plusMinutes(60), 104, 104, 99, 100), 10_000)).isEmpty();
    }

    @Test
    void limitFill_sizesOnTheFillPrice_andCountsMoreRToTarget() {
        OpenPosition position = OpenPosition.fillLimit(PLAN, candle(Timeframe.M30, T0, 101, 101, 97, 99), 97.5, 100, 0, "LIMIT_M15");

        Trade trade = position.exitOn(candle(Timeframe.M30, T0.plusMinutes(30), 100, 121, 99, 118), 10_000).orElseThrow();

        assertThat(trade.entryType()).isEqualTo("LIMIT_M15");
        assertThat(trade.rMultiple()).isEqualTo(9);
        assertThat(trade.pnl()).isEqualTo(900);
    }

    @Test
    void fillBar_judgesOnlyTheStop_notTheTarget() {
        OpenPosition position = OpenPosition.fillLimit(PLAN, candle(Timeframe.M30, T0, 101, 125, 97, 99), 97.5, 100, 0, "LIMIT_M15");

        assertThat(position.exitOnFillBar(candle(Timeframe.M30, T0, 101, 125, 97, 99), 10_000)).isEmpty();
        assertThat(position.exitOnFillBar(candle(Timeframe.M30, T0, 101, 125, 94, 99), 10_000))
                .hasValueSatisfying(t -> assertThat(t.exitReason()).isEqualTo(ExitReason.SL));
    }

    @Test
    void entryBarOpeningBeyondALevel_cancelsTheTrade() {
        assertThat(OpenPosition.enter(PLAN, candle(Timeframe.M30, T0, 94, 96, 93, 95), 100)).isEmpty();
        assertThat(OpenPosition.enter(PLAN, candle(Timeframe.M30, T0, 121, 122, 119, 120), 100)).isEmpty();
    }
}
