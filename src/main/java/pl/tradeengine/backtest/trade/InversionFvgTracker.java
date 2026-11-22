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
 * Bullish inversion FVGs (IFVG) of one lower timeframe: bearish gaps that a candle closed above, so
 * the former resistance is now support a limit buy can rest on.
 *
 * <p>A bearish gap waits for a close above its {@code upper} edge — a wick through it does not invert
 * it. Once inverted, the zone (dated from the inversion) is open until a candle closes below its
 * {@code lower} edge. Gaps and zones older than {@code maxAge} are dropped, as in
 * {@link BullishFvgTracker}.
 */
public class InversionFvgTracker {

    private final FvgDetector detector = new FvgDetector();
    private final List<OpenFvg> bearish = new ArrayList<>();
    private final List<OpenFvg> inverted = new ArrayList<>();
    private final Duration maxAge;

    public InversionFvgTracker(Duration maxAge) {
        this.maxAge = maxAge;
    }

    /** A closed candle: invalidates and inverts older zones with its close, then may create a new gap. */
    public void onCandle(PriceCandle candle) {
        double close = candle.close().doubleValue();
        var oldest = candle.closeTime().minus(maxAge);
        inverted.removeIf(z -> close < z.lower() || z.createdAt().isBefore(oldest));
        bearish.removeIf(z -> z.createdAt().isBefore(oldest));
        bearish.removeIf(z -> {
            if (close <= z.upper()) {
                return false;
            }
            inverted.add(new OpenFvg(z.timeframe(), z.lower(), z.upper(), candle.closeTime()));
            return true;
        });
        Optional<FvgZone> zone = detector.detect(candle);
        zone.filter(z -> z.getDirection() == Direction.SHORT)
                .ifPresent(z -> bearish.add(new OpenFvg(candle.timeframe(),
                        z.getLowerPrice().doubleValue(), z.getUpperPrice().doubleValue(), candle.closeTime())));
    }

    /** The inverted zone closest below {@code ceiling} that lies entirely above {@code floor}. */
    public Optional<OpenFvg> highestBetween(double floor, double ceiling) {
        return inverted.stream()
                .filter(z -> z.upper() < ceiling && z.lower() > floor)
                .max(Comparator.comparingDouble(OpenFvg::upper));
    }
}
