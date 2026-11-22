package pl.tradeengine.backtest.structure;

import pl.tradeengine.backtest.trade.OpenFvg;

import java.time.ZonedDateTime;

/**
 * One structural event, in real prices.
 *
 * @param time      open time of the bar it happened on
 * @param trend     the structure it belongs to — for an MSS, the new trend
 * @param level     the price that was reached: the leg's extreme for SWEEP/BOS, the protected level for
 *                  MSS, the touched edge of the zone for FVG_TOUCH
 * @param levelTime open time of the bar that made {@code level}
 * @param zone      the touched FVG, only for FVG_TOUCH; {@code createdAt} is the open of its first candle
 */
public record StructureEvent(StructureEventType type, ZonedDateTime time, Trend trend,
                             double level, ZonedDateTime levelTime, OpenFvg zone) {
}
