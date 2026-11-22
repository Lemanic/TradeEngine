package pl.tradeengine.backtest.trade;

import org.junit.jupiter.api.Test;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Timeframe;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.tradeengine.TestFixtures.candle;

class PriceMirrorTest {

    private static final ZonedDateTime T0 = ZonedDateTime.parse("2025-03-03T00:00:00Z");

    @Test
    void mirroredCandle_swapsHighAndLow() {
        PriceCandle m = PriceMirror.candle(candle(Timeframe.H1, T0, 100, 110, 95, 105));

        assertThat(m.open()).isEqualByComparingTo(BigDecimal.valueOf(-100));
        assertThat(m.high()).isEqualByComparingTo(BigDecimal.valueOf(-95));
        assertThat(m.low()).isEqualByComparingTo(BigDecimal.valueOf(-110));
        assertThat(m.close()).isEqualByComparingTo(BigDecimal.valueOf(-105));
    }

    @Test
    void bearishGapInRealPrices_isABullishEntryZoneInTheMirror() {
        BullishFvgTracker tracker = new BullishFvgTracker(java.time.Duration.ofDays(7));
        tracker.onCandle(PriceMirror.candle(candle(Timeframe.M15, T0, 112, 112, 105, 106)));
        tracker.onCandle(PriceMirror.candle(candle(Timeframe.M15, T0.plusMinutes(15), 106, 106, 98, 99)));
        tracker.onCandle(PriceMirror.candle(candle(Timeframe.M15, T0.plusMinutes(30), 99, 100, 95, 96)));

        // Real bearish gap [100, 105] above a short alert at 96 with SL at 112.
        assertThat(tracker.highestBetween(-112, -96))
                .hasValueSatisfying(z -> {
                    assertThat(z.lower()).isEqualTo(-105);
                    assertThat(z.upper()).isEqualTo(-100);
                });
    }

    /** Short at 100 with SL 105 and TP 80, simulated as a long at -100 with SL -105 and TP -80. */
    @Test
    void shortSimulatedOnMirroredPrices_winsWhenPriceFalls() {
        SignalPlan mirrored = new SignalPlan(T0, SignalOutcome.PLANNED, -100, -105.0, -80.0,
                new OpenFvg(Timeframe.H4, -85, -80, T0.minusHours(4)), 4.0, T0.minusHours(8), Direction.LONG);
        OpenPosition position = OpenPosition.enter(mirrored,
                PriceMirror.candle(candle(Timeframe.M15, T0, 100, 101, 99, 100)), 100).orElseThrow();

        Trade simulated = position.exitOn(PriceMirror.candle(candle(Timeframe.M15, T0.plusMinutes(15), 90, 91, 79, 80)), 10_000)
                .orElseThrow();
        Trade real = PriceMirror.shortTrade(simulated);

        assertThat(real.direction()).isEqualTo(Direction.SHORT);
        assertThat(real.exitReason()).isEqualTo(ExitReason.TP);
        assertThat(real.entryPrice()).isEqualTo(100);
        assertThat(real.stopLoss()).isEqualTo(105);
        assertThat(real.takeProfit()).isEqualTo(80);
        assertThat(real.exitPrice()).isEqualTo(80);
        assertThat(real.pnl()).isEqualTo(400);
        assertThat(real.rMultiple()).isEqualTo(4);
    }

    @Test
    void shortPlan_isMappedBackToRealPrices() {
        SignalPlan real = PriceMirror.shortPlan(new SignalPlan(T0, SignalOutcome.PLANNED, -100, -105.0, -80.0,
                new OpenFvg(Timeframe.H4, -85, -80, T0), 4.0, T0, Direction.LONG));

        assertThat(real.direction()).isEqualTo(Direction.SHORT);
        assertThat(real.close()).isEqualTo(100);
        assertThat(real.stopLoss()).isEqualTo(105);
        assertThat(real.takeProfit()).isEqualTo(80);
        assertThat(real.target().lower()).isEqualTo(80);
        assertThat(real.target().upper()).isEqualTo(85);
    }
}
