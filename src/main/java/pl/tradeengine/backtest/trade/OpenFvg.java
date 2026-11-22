package pl.tradeengine.backtest.trade;

import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;

/**
 * A bearish FVG that price has not traded through yet. It may already be touched — as long as
 * {@code upper} is still above price, the zone has its far edge left "to eat".
 *
 * @param createdAt close of the third candle, i.e. the first moment the zone is known
 */
public record OpenFvg(Timeframe timeframe, double lower, double upper, ZonedDateTime createdAt) {
}
