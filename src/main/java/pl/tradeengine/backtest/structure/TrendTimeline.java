package pl.tradeengine.backtest.structure;

import pl.tradeengine.domain.model.PriceCandle;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The trend a {@link MarketStructureTracker} reported after each closed candle of one timeframe, for
 * asking "what was the trend at this moment" from another timeframe. A candle's trend becomes known at
 * its close, so a lower-timeframe bar inside a D1 candle sees the trend of the D1 candle before it.
 */
public class TrendTimeline {

    private final List<ZonedDateTime> closeTimes = new ArrayList<>();
    private final List<Trend> trends = new ArrayList<>();

    public TrendTimeline(List<PriceCandle> candles, MarketStructureTracker tracker) {
        for (PriceCandle candle : candles) {
            tracker.onCandle(candle);
            closeTimes.add(candle.closeTime());
            trends.add(tracker.trend().orElse(null));
        }
    }

    /** The trend after the last candle that closed at or before {@code time}; empty before it is known. */
    public Optional<Trend> at(ZonedDateTime time) {
        int i = Collections.binarySearch(closeTimes, time);
        int last = i >= 0 ? i : -i - 2;
        return last < 0 ? Optional.empty() : Optional.ofNullable(trends.get(last));
    }
}
