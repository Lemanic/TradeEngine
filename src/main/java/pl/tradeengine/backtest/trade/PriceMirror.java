package pl.tradeengine.backtest.trade;

import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.PriceCandle;

/**
 * A short on price P is a long on −P. Negating every price turns highs into lows, a bearish FVG
 * into a bullish one and a stop above entry into a stop below it, so the long-only planner,
 * trackers and {@link OpenPosition} trade shorts unchanged. This class maps candles into that
 * mirrored world and maps the resulting plans and trades back to real prices.
 */
public final class PriceMirror {

    private PriceMirror() {
    }

    public static PriceCandle candle(PriceCandle c) {
        return new PriceCandle(c.symbol(), c.timeframe(), c.openTime(), c.closeTime(),
                c.open().negate(), c.low().negate(), c.high().negate(), c.close().negate());
    }

    /** A trade simulated on mirrored prices, as the short it really was. P&L and R need no change. */
    public static Trade shortTrade(Trade t) {
        return new Trade(t.signalTime(), t.entryTime(), -t.entryPrice(), -t.stopLoss(), -t.takeProfit(),
                t.targetTimeframe(), t.quantity(), t.exitTime(), -t.exitPrice(), t.exitReason(), t.pnl(),
                t.rMultiple(), t.equityAfter(), t.entryType(), Direction.SHORT);
    }

    /** A plan made on mirrored prices, as the short it really was. */
    public static SignalPlan shortPlan(SignalPlan p) {
        OpenFvg target = p.target() == null ? null
                : new OpenFvg(p.target().timeframe(), -p.target().upper(), -p.target().lower(), p.target().createdAt());
        return new SignalPlan(p.signalTime(), p.outcome(), -p.close(), negate(p.stopLoss()), negate(p.takeProfit()),
                target, p.rewardToRisk(), p.legStart(), Direction.SHORT);
    }

    private static Double negate(Double v) {
        return v == null ? null : -v;
    }
}
