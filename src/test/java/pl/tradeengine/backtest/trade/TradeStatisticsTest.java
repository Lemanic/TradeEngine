package pl.tradeengine.backtest.trade;

import org.junit.jupiter.api.Test;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.Timeframe;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TradeStatisticsTest {

    private static final ZonedDateTime T0 = ZonedDateTime.parse("2025-03-03T00:00:00Z");

    private static Trade trade(ExitReason reason, double pnl, double r) {
        return new Trade(T0, T0, 100, 95, 120, Timeframe.H4, 20, T0.plusHours(2), 0, reason, pnl, r, 0, "MARKET", Direction.LONG);
    }

    /** +300, -100, -100, +200 on 10 000: balance 10 300 → 10 200 → 10 100 → 10 300. */
    private static final List<Trade> TRADES = List.of(
            trade(ExitReason.TP, 300, 3),
            trade(ExitReason.SL, -100, -1),
            trade(ExitReason.SL, -100, -1),
            trade(ExitReason.TP, 200, 2),
            trade(ExitReason.OPEN_AT_END, 999, 9));

    @Test
    void openTradeAtTheEnd_isLeftOut() {
        TradeStatistics st = TradeStatistics.of(TRADES, 10_000);

        assertThat(st.trades()).isEqualTo(4);
        assertThat(st.netPnl()).isEqualTo(300);
        assertThat(st.finalCapital()).isEqualTo(10_300);
    }

    @Test
    void ratios() {
        TradeStatistics st = TradeStatistics.of(TRADES, 10_000);

        assertThat(st.winRatePct()).isEqualTo(50);
        assertThat(st.profitFactor()).isEqualTo(2.5);
        assertThat(st.expectancy()).isEqualTo(75);
        assertThat(st.avgWinToAvgLoss()).isEqualTo(2.5);
        assertThat(st.avgR()).isEqualTo(0.75);
        assertThat(st.avgHolding()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void drawdownAndStreaks() {
        TradeStatistics st = TradeStatistics.of(TRADES, 10_000);

        assertThat(st.maxDrawdown()).isEqualTo(200);
        assertThat(st.maxDrawdownPct()).isCloseTo(200 / 10_300.0 * 100, within(1e-9));
        assertThat(st.maxWinStreak()).isEqualTo(1);
        assertThat(st.maxLossStreak()).isEqualTo(2);
    }

    @Test
    void breakEvenExit_isNeitherAWinNorALoss() {
        TradeStatistics st = TradeStatistics.of(List.of(
                trade(ExitReason.SL, -100, -1),
                trade(ExitReason.BE, 0, 0),
                trade(ExitReason.SL, -100, -1)), 10_000);

        assertThat(st.trades()).isEqualTo(3);
        assertThat(st.losses()).isEqualTo(2);
        assertThat(st.breakEvens()).isEqualTo(1);
        assertThat(st.avgLoss()).isEqualTo(-100);
        assertThat(st.maxLossStreak()).isEqualTo(1);
    }

    @Test
    void noTrades_givesZeros() {
        TradeStatistics st = TradeStatistics.of(List.of(), 10_000);

        assertThat(st.trades()).isZero();
        assertThat(st.winRatePct()).isZero();
        assertThat(st.finalCapital()).isEqualTo(10_000);
    }
}
