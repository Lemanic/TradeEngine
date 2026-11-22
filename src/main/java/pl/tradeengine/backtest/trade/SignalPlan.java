package pl.tradeengine.backtest.trade;

import pl.tradeengine.domain.model.Direction;

import java.time.ZonedDateTime;

/**
 * The planner's verdict on one signal. Prices are planned against the signal bar's close; the
 * actual entry happens at the next bar's open.
 *
 * @param stopLoss   null only when the signal was rejected before SL was computed
 * @param takeProfit null when no target was found
 * @param direction  LONG unless a short was planned on mirrored prices and mapped back
 */
public record SignalPlan(
        ZonedDateTime signalTime,
        SignalOutcome outcome,
        double close,
        Double stopLoss,
        Double takeProfit,
        OpenFvg target,
        Double rewardToRisk,
        ZonedDateTime legStart,
        Direction direction
) {

    static SignalPlan rejected(ZonedDateTime signalTime, SignalOutcome outcome, double close) {
        return new SignalPlan(signalTime, outcome, close, null, null, null, null, null, Direction.LONG);
    }

    public SignalPlan withOutcome(SignalOutcome newOutcome) {
        return new SignalPlan(signalTime, newOutcome, close, stopLoss, takeProfit, target, rewardToRisk, legStart, direction);
    }

    /** The same plan with its stop moved, e.g. to a lower timeframe's structure once the entry is triggered there. */
    public SignalPlan withStopLoss(double newStopLoss, double newRewardToRisk) {
        return new SignalPlan(signalTime, outcome, close, newStopLoss, takeProfit, target, newRewardToRisk, legStart, direction);
    }
}
