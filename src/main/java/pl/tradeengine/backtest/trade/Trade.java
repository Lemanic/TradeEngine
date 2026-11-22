package pl.tradeengine.backtest.trade;

import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;

/**
 * A closed long position.
 *
 * @param entryTime open time of the entry bar (the bar after the signal)
 * @param exitTime  open time of the bar in which SL or TP was hit
 * @param rMultiple result in units of initial risk: +2.0 means twice the distance to SL was won
 * @param entryType {@code MARKET} for the next bar's open, {@code LIMIT_M15} / {@code LIMIT_M5} for a
 *                  limit order resting on that timeframe's FVG
 * @param direction LONG or SHORT; prices are always real prices, whichever side was traded
 */
public record Trade(
        ZonedDateTime signalTime,
        ZonedDateTime entryTime,
        double entryPrice,
        double stopLoss,
        double takeProfit,
        Timeframe targetTimeframe,
        double quantity,
        ZonedDateTime exitTime,
        double exitPrice,
        ExitReason exitReason,
        double pnl,
        double rMultiple,
        double equityAfter,
        String entryType,
        Direction direction
) {

    public boolean closed() {
        return exitReason != ExitReason.OPEN_AT_END;
    }
}
