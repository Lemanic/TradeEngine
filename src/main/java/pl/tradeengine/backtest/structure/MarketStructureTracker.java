package pl.tradeengine.backtest.structure;

import pl.tradeengine.backtest.indicators.FvgDetector;
import pl.tradeengine.backtest.trade.OpenFvg;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Market structure of one timeframe, from that timeframe's closed candles alone.
 *
 * <p>Described for a downtrend. A <b>leg</b> runs down from the protected high to its lowest low L and
 * leaves bearish FVGs on the way. A pullback whose wick trades into any of them arms the leg — even the
 * candle right after the gap formed, and even when that candle made the leg's low: a pullback that starts
 * at once is still a pullback. H is the highest high since L. Once armed, price reaching L again shifts
 * the structure: H becomes the new protected high and a new leg starts from it, keeping only the FVGs that formed below H. A wick to L is
 * a {@link StructureEventType#SWEEP}, a close below it a {@link StructureEventType#BOS}; after a sweep, a
 * later close below the swept low still confirms the BOS. A new low before any FVG was touched only
 * extends the leg.
 *
 * <p>A wick above the protected high that closes back below it is no MSS: the protected high moves up to
 * that wick ({@link StructureEventType#PROTECTED_SWEEP}), as many times as it happens. Only a close above
 * the protected high is an {@link StructureEventType#MSS}: the trend flips, L becomes
 * the protected low, and the move up from L — with the bullish FVGs it left — is the uptrend's first leg.
 *
 * <p>With {@code counterLegMss}, a downtrend can also flip before the protected high: the move up from L
 * to H is a counter-leg, and when a pullback from H trades into one of its bullish FVGs and a later close
 * clears H, that is an MSS too — the pullback's low becomes the protected low. A wick above H without a
 * close only moves H up, as with the protected high. A new low below L cancels the counter-leg.
 *
 * <p>The uptrend is the same on mirrored prices, so state is held in <i>trend space</i>: real prices in
 * a downtrend, negated in an uptrend, where a high becomes a low and a bullish FVG a bearish one (as in
 * {@code PriceMirror}). An MSS negates the stored prices; events are reported in real prices.
 *
 * <p>The first candle's range seeds a downtrend; {@link #trend()} stays empty until the first BOS or MSS.
 * Within one bar, a new extreme of the leg is assumed to come before any pullback.
 */
public class MarketStructureTracker {

    private final boolean counterLegMss;
    private final FvgDetector detector = new FvgDetector();
    private final List<Zone> withTrend = new ArrayList<>();
    private final List<Zone> counterTrend = new ArrayList<>();

    /** +1: trend space is real prices (downtrend); −1: negated (uptrend). */
    private double sign = 1;
    private Timeframe timeframe;
    private boolean started;
    private boolean known;

    private double protectedHigh;
    private ZonedDateTime protectedTime;
    private double legLow;
    private ZonedDateTime legLowTime;
    private double pullbackHigh;
    private ZonedDateTime pullbackHighTime;
    private boolean armed;
    private Double pendingBos;
    private ZonedDateTime pendingBosTime;
    /** The counter-leg's pullback: lowest low since H, and whether it traded into a counter-trend FVG. */
    private double counterPullbackLow;
    private ZonedDateTime counterPullbackLowTime;
    private boolean counterArmed;

    public MarketStructureTracker() {
        this(false);
    }

    /** @param counterLegMss also flip the trend when a counter-leg completes its own FVG pullback and breaks out */
    public MarketStructureTracker(boolean counterLegMss) {
        this.counterLegMss = counterLegMss;
    }

    /** A gap in trend space; {@code start} is the open of its first candle, where the move that left it began. */
    private record Zone(double lower, double upper, ZonedDateTime start) {
        Zone mirrored() {
            return new Zone(-upper, -lower, start);
        }
    }

    public List<StructureEvent> onCandle(PriceCandle candle) {
        double high = sign > 0 ? candle.high().doubleValue() : -candle.low().doubleValue();
        double low = sign > 0 ? candle.low().doubleValue() : -candle.high().doubleValue();
        double close = sign * candle.close().doubleValue();
        ZonedDateTime t = candle.openTime();
        List<StructureEvent> events = new ArrayList<>();

        if (!started) {
            started = true;
            timeframe = candle.timeframe();
            protectedHigh = high;
            protectedTime = t;
            startLeg(low, t);
            detect(candle);
            return events;
        }

        if (close > protectedHigh) {
            flip(high, t, protectedHigh, protectedTime, legLow, legLowTime, counterTrend, events);
        } else if (counterArmed && close > pullbackHigh) {
            ZonedDateTime pullbackStart = counterPullbackLowTime;
            List<Zone> firstLeg = counterTrend.stream().filter(z -> !z.start().isBefore(pullbackStart)).toList();
            flip(high, t, pullbackHigh, pullbackHighTime, counterPullbackLow, counterPullbackLowTime, firstLeg, events);
        } else {
            if (high > protectedHigh) {
                events.add(event(StructureEventType.PROTECTED_SWEEP, t, protectedHigh, protectedTime, null));
                protectedHigh = high;
                protectedTime = t;
            }
            if (pendingBos != null && close < pendingBos) {
                events.add(event(StructureEventType.BOS, t, pendingBos, pendingBosTime, null));
                pendingBos = null;
                known = true;
            }
            if (low <= legLow) {
                if (armed) {
                    shift(low, close, t, events);
                } else {
                    startLeg(low, t);
                }
            } else {
                pullback(high, low, t, events);
            }
        }

        detect(candle);
        return events;
    }

    public Optional<Trend> trend() {
        return known ? Optional.of(currentTrend()) : Optional.empty();
    }

    /** The level whose close-through is an MSS: the protected high in a downtrend, the protected low in an uptrend. */
    public double protectedLevel() {
        return sign * protectedHigh;
    }

    private void pullback(double high, double low, ZonedDateTime t, List<StructureEvent> events) {
        if (high > pullbackHigh) {
            pullbackHigh = high;
            pullbackHighTime = t;
            // A higher high extends the counter-leg; once its pullback is armed, a wick only moves H.
            if (!counterArmed) {
                counterPullbackLow = Double.POSITIVE_INFINITY;
                counterPullbackLowTime = null;
            }
        } else if (counterLegMss) {
            counterPullback(low, t, events);
        }
        Optional<Zone> touched = withTrend.stream()
                .filter(z -> high >= z.lower())
                .min(Comparator.comparingDouble(Zone::lower));
        if (touched.isPresent()) {
            withTrend.removeIf(z -> high >= z.lower());
            if (!armed) {
                armed = true;
                events.add(event(StructureEventType.FVG_TOUCH, t, touched.get().lower(), touched.get().start(), touched.get()));
            }
        }
        // An opposite gap the pullback already traded back into cannot be the next trend's first leg.
        counterTrend.removeIf(z -> low <= z.upper());
    }

    private void counterPullback(double low, ZonedDateTime t, List<StructureEvent> events) {
        if (low < counterPullbackLow) {
            counterPullbackLow = low;
            counterPullbackLowTime = t;
        }
        Optional<Zone> touched = counterTrend.stream()
                .filter(z -> low <= z.upper())
                .max(Comparator.comparingDouble(Zone::upper));
        if (touched.isPresent() && !counterArmed) {
            counterArmed = true;
            // Drawn as the counter-leg's: a bullish gap in a downtrend, a bearish one in an uptrend.
            StructureEvent e = event(StructureEventType.FVG_TOUCH, t, touched.get().upper(), touched.get().start(), touched.get());
            events.add(new StructureEvent(e.type(), e.time(), opposite(e.trend()), e.level(), e.levelTime(), e.zone()));
        }
    }

    private void shift(double low, double close, ZonedDateTime t, List<StructureEvent> events) {
        boolean closedThrough = close < legLow;
        events.add(event(closedThrough ? StructureEventType.BOS : StructureEventType.SWEEP, t, legLow, legLowTime, null));
        if (closedThrough) {
            pendingBos = null;
            known = true;
        } else {
            pendingBos = legLow;
            pendingBosTime = legLowTime;
        }
        protectedHigh = pullbackHigh;
        protectedTime = pullbackHighTime;
        ZonedDateTime newLegStart = pullbackHighTime;
        withTrend.removeIf(z -> z.start().isBefore(newLegStart));
        startLeg(low, t);
    }

    /**
     * Flips the trend: {@code brokenLevel} was closed through, {@code newProtected} (a trend-space low) becomes
     * the new trend's protected level, and {@code firstLeg} — counter-trend gaps — the FVGs of its first leg.
     */
    private void flip(double high, ZonedDateTime t, double brokenLevel, ZonedDateTime brokenTime,
                      double newProtected, ZonedDateTime newProtectedTime, List<Zone> firstLeg,
                      List<StructureEvent> events) {
        boolean barIsExtreme = high >= pullbackHigh;
        double newLegLow = -(barIsExtreme ? high : pullbackHigh);
        ZonedDateTime newLegLowTime = barIsExtreme ? t : pullbackHighTime;
        protectedHigh = -newProtected;
        protectedTime = newProtectedTime;
        List<Zone> mirrored = firstLeg.stream().map(Zone::mirrored).toList();
        withTrend.clear();
        withTrend.addAll(mirrored);
        pendingBos = null;
        known = true;

        // Reported before the sign flips, in the old trend space, then tagged with the new trend.
        StructureEvent mss = event(StructureEventType.MSS, t, brokenLevel, brokenTime, null);
        sign = -sign;
        events.add(new StructureEvent(mss.type(), mss.time(), currentTrend(), mss.level(), mss.levelTime(), null));
        startLeg(newLegLow, newLegLowTime);
    }

    private void startLeg(double low, ZonedDateTime t) {
        legLow = low;
        legLowTime = t;
        pullbackHigh = Double.NEGATIVE_INFINITY;
        pullbackHighTime = null;
        armed = false;
        counterTrend.clear();
        counterPullbackLow = Double.POSITIVE_INFINITY;
        counterPullbackLowTime = null;
        counterArmed = false;
    }

    private void detect(PriceCandle candle) {
        Optional<FvgZone> zone = detector.detect(candle);
        if (zone.isEmpty()) {
            return;
        }
        FvgZone z = zone.get();
        double lower = z.getLowerPrice().doubleValue();
        double upper = z.getUpperPrice().doubleValue();
        ZonedDateTime start = candle.openTime().minus(candle.timeframe().getDuration().multipliedBy(2));
        Zone inTrendSpace = sign > 0 ? new Zone(lower, upper, start) : new Zone(-upper, -lower, start);
        Direction withTrendDirection = sign > 0 ? Direction.SHORT : Direction.LONG;
        (z.getDirection() == withTrendDirection ? withTrend : counterTrend).add(inTrendSpace);
    }

    private Trend currentTrend() {
        return sign > 0 ? Trend.DOWN : Trend.UP;
    }

    private static Trend opposite(Trend trend) {
        return trend == Trend.UP ? Trend.DOWN : Trend.UP;
    }

    private StructureEvent event(StructureEventType type, ZonedDateTime t, double level, ZonedDateTime levelTime, Zone zone) {
        OpenFvg real = zone == null ? null : sign > 0
                ? new OpenFvg(timeframe, zone.lower(), zone.upper(), zone.start())
                : new OpenFvg(timeframe, -zone.upper(), -zone.lower(), zone.start());
        return new StructureEvent(type, t, currentTrend(), sign * level, levelTime, real);
    }
}
