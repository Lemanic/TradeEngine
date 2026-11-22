package pl.tradeengine.backtest.trade;

import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.PriceCandle;

import java.util.Optional;

/**
 * A long between entry and exit. OHLC alone cannot tell whether SL or TP was hit first inside one
 * bar, so a bar that reaches both counts as SL — the pessimistic reading.
 *
 * <p>Optionally moves SL to entry once price has gained {@code breakEvenAtR} times the initial
 * risk. The move takes effect from the <b>next</b> bar: within the bar that reached the trigger,
 * OHLC cannot tell whether the high came before the low, so that bar is still judged against the
 * original SL.
 */
public class OpenPosition {

    private final SignalPlan plan;
    private final PriceCandle entryBar;
    private final double entryPrice;
    private final double quantity;
    private final String entryType;
    private final double breakEvenTrigger;
    private double stop;
    private boolean atBreakEven;

    private OpenPosition(SignalPlan plan, PriceCandle entryBar, double entryPrice, double quantity,
                         double breakEvenAtR, String entryType) {
        this.plan = plan;
        this.entryBar = entryBar;
        this.entryPrice = entryPrice;
        this.quantity = quantity;
        this.entryType = entryType;
        this.stop = plan.stopLoss();
        this.breakEvenTrigger = breakEvenAtR > 0
                ? entryPrice + breakEvenAtR * (entryPrice - plan.stopLoss())
                : Double.POSITIVE_INFINITY;
    }

    /**
     * Enters at the open of {@code entryBar}, risking {@code riskAmount} on the distance to SL.
     * Empty when that open already lies beyond SL or TP.
     */
    public static Optional<OpenPosition> enter(SignalPlan plan, PriceCandle entryBar, double riskAmount) {
        return enter(plan, entryBar, riskAmount, 0);
    }

    /** As {@link #enter(SignalPlan, PriceCandle, double)}; {@code breakEvenAtR} 0 never moves SL. */
    public static Optional<OpenPosition> enter(SignalPlan plan, PriceCandle entryBar, double riskAmount, double breakEvenAtR) {
        double open = entryBar.open().doubleValue();
        if (open <= plan.stopLoss() || open >= plan.takeProfit()) {
            return Optional.empty();
        }
        return Optional.of(new OpenPosition(plan, entryBar, open, riskAmount / (open - plan.stopLoss()), breakEvenAtR, "MARKET"));
    }

    /**
     * A limit buy filled at {@code price} inside {@code fillBar}. The caller has checked that the bar
     * traded at that price; sizing uses the fill price, so a lower fill means a bigger position for
     * the same risk and a bigger R to TP.
     */
    public static OpenPosition fillLimit(SignalPlan plan, PriceCandle fillBar, double price, double riskAmount,
                                         double breakEvenAtR, String entryType) {
        return new OpenPosition(plan, fillBar, price, riskAmount / (price - plan.stopLoss()), breakEvenAtR, entryType);
    }

    /**
     * The bar a limit order filled in. Its high may have come before the fill, so only SL counts
     * here — TP and the break-even trigger wait for the next bar.
     */
    public Optional<Trade> exitOnFillBar(PriceCandle bar, double equityBefore) {
        if (bar.low().doubleValue() <= stop) {
            return Optional.of(close(bar, stop, ExitReason.SL, equityBefore));
        }
        return Optional.empty();
    }

    /** Checks one bar, the entry bar included. A gap through a level fills at the open, not at the level. */
    public Optional<Trade> exitOn(PriceCandle bar, double equityBefore) {
        double open = bar.open().doubleValue();
        double tp = plan.takeProfit();
        ExitReason stopReason = atBreakEven ? ExitReason.BE : ExitReason.SL;

        if (open <= stop) {
            return Optional.of(close(bar, open, stopReason, equityBefore));
        }
        if (open >= tp) {
            return Optional.of(close(bar, open, ExitReason.TP, equityBefore));
        }
        if (bar.low().doubleValue() <= stop) {
            return Optional.of(close(bar, stop, stopReason, equityBefore));
        }
        if (bar.high().doubleValue() >= tp) {
            return Optional.of(close(bar, tp, ExitReason.TP, equityBefore));
        }
        if (!atBreakEven && bar.high().doubleValue() >= breakEvenTrigger) {
            stop = entryPrice;
            atBreakEven = true;
        }
        return Optional.empty();
    }

    public Trade markOpenAtEnd(PriceCandle lastBar, double equityBefore) {
        return close(lastBar, lastBar.close().doubleValue(), ExitReason.OPEN_AT_END, equityBefore);
    }

    private Trade close(PriceCandle bar, double exitPrice, ExitReason reason, double equityBefore) {
        double pnl = quantity * (exitPrice - entryPrice);
        double r = (exitPrice - entryPrice) / (entryPrice - plan.stopLoss());
        double equityAfter = reason == ExitReason.OPEN_AT_END ? equityBefore : equityBefore + pnl;
        return new Trade(plan.signalTime(), entryBar.openTime(), entryPrice, plan.stopLoss(), plan.takeProfit(),
                plan.target().timeframe(), quantity, bar.openTime(), exitPrice, reason, pnl, r, equityAfter, entryType,
                Direction.LONG);
    }
}
