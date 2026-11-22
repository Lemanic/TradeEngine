package pl.tradeengine.backtest.trade;

import pl.tradeengine.backtest.indicators.FvgDetector;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.PriceCandle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Open bullish FVGs of one lower timeframe — the zones a limit buy can rest on after a signal.
 *
 * <p>A zone is open until price trades down to its {@code lower} edge; touching {@code upper} (where
 * the limit sits) does not close it. Zones older than {@code maxAge} are dropped: an entry zone has to
 * lie between the signal's swing low and close, so only recent ones can qualify, and keeping years of
 * unfilled gaps would make every candle scan them all.
 */
public class BullishFvgTracker {

    private final FvgDetector detector = new FvgDetector();
    private final List<OpenFvg> open = new ArrayList<>();
    private final Duration maxAge;

    public BullishFvgTracker(Duration maxAge) {
        this.maxAge = maxAge;
    }

    /** A closed candle: first fills older zones with its low, then may create a new one. */
    public void onCandle(PriceCandle candle) {
        double low = candle.low().doubleValue();
        var oldest = candle.closeTime().minus(maxAge);
        open.removeIf(z -> low <= z.lower() || z.createdAt().isBefore(oldest));
        Optional<FvgZone> zone = detector.detect(candle);
        zone.filter(z -> z.getDirection() == Direction.LONG)
                .ifPresent(z -> open.add(new OpenFvg(candle.timeframe(),
                        z.getLowerPrice().doubleValue(), z.getUpperPrice().doubleValue(), candle.closeTime())));
    }

    /** The open zone closest below {@code ceiling} that lies entirely above {@code floor}. */
    public Optional<OpenFvg> highestBetween(double floor, double ceiling) {
        return open.stream()
                .filter(z -> z.upper() < ceiling && z.lower() > floor)
                .max(Comparator.comparingDouble(OpenFvg::upper));
    }
}
