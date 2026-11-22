package pl.tradeengine.backtest.trade;

import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Turns a long signal into SL and TP, using only what is known at the signal bar's close.
 *
 * <ul>
 *   <li><b>SL</b> — the lowest low of the last {@code slLookback} bars, signal bar included, but
 *       never closer to the close than {@code minStopAtr} × ATR. A swing low a few ticks under the
 *       close would otherwise size a huge position on noise and turn one lucky TP into +100R.</li>
 *   <li><b>Leg</b> — the current down-leg starts at the highest high of the last
 *       {@code legLookback} bars. Only FVGs created after that high belong to it; the ones above an
 *       earlier lower high are another leg's business.</li>
 *   <li><b>TP</b> — the upper edge of an open bearish FVG of the leg, searched on the target
 *       timeframes in order (H4 first, H1 only when H4 has none). {@link TargetSelection} picks
 *       the highest such zone or the nearest one above price.</li>
 *   <li>The trade is dropped when the planned reward is below {@code minRewardToRisk}.</li>
 * </ul>
 */
public class LongTradePlanner {

    private final int slLookback;
    private final int legLookback;
    private final double minRewardToRisk;
    private final List<Timeframe> targetTimeframes;
    private final double minStopAtr;
    private final int atrPeriod;
    private final TargetSelection selection;

    /** SL purely from the swing low, no ATR floor. */
    public LongTradePlanner(int slLookback, int legLookback, double minRewardToRisk, List<Timeframe> targetTimeframes) {
        this(slLookback, legLookback, minRewardToRisk, targetTimeframes, 0, 0);
    }

    /**
     * @param minStopAtr minimum SL distance in ATRs; 0 turns the floor off
     * @param atrPeriod  ATR as a simple mean of the last {@code atrPeriod} true ranges
     */
    public LongTradePlanner(int slLookback, int legLookback, double minRewardToRisk, List<Timeframe> targetTimeframes,
                            double minStopAtr, int atrPeriod) {
        this(slLookback, legLookback, minRewardToRisk, targetTimeframes, minStopAtr, atrPeriod, TargetSelection.HIGHEST);
    }

    public LongTradePlanner(int slLookback, int legLookback, double minRewardToRisk, List<Timeframe> targetTimeframes,
                            double minStopAtr, int atrPeriod, TargetSelection selection) {
        this.slLookback = slLookback;
        this.legLookback = legLookback;
        this.minRewardToRisk = minRewardToRisk;
        this.targetTimeframes = List.copyOf(targetTimeframes);
        this.minStopAtr = minStopAtr;
        this.atrPeriod = atrPeriod;
        this.selection = selection;
    }

    /**
     * @param candles the trigger timeframe's history
     * @param index   the signal bar
     * @param zones   open HTF zones as of the signal bar's close
     */
    public SignalPlan plan(List<PriceCandle> candles, int index, List<OpenFvg> zones) {
        PriceCandle signal = candles.get(index);
        double close = signal.close().doubleValue();
        if (index + 1 < Math.max(Math.max(slLookback, legLookback), atrPeriod + 1)) {
            return SignalPlan.rejected(signal.closeTime(), SignalOutcome.WARMUP, close);
        }

        double sl = Double.MAX_VALUE;
        for (int i = index - slLookback + 1; i <= index; i++) {
            sl = Math.min(sl, candles.get(i).low().doubleValue());
        }
        if (minStopAtr > 0) {
            sl = Math.min(sl, close - minStopAtr * atr(candles, index));
        }
        if (sl >= close) {
            return SignalPlan.rejected(signal.closeTime(), SignalOutcome.NO_RISK, close);
        }

        PriceCandle legHigh = candles.get(index);
        for (int i = index - legLookback + 1; i <= index; i++) {
            if (candles.get(i).high().compareTo(legHigh.high()) > 0) {
                legHigh = candles.get(i);
            }
        }
        ZonedDateTime legStart = legHigh.openTime();

        Comparator<OpenFvg> byUpper = Comparator.comparingDouble(OpenFvg::upper);
        Optional<OpenFvg> target = Optional.empty();
        for (Timeframe tf : targetTimeframes) {
            var candidates = zones.stream()
                    .filter(z -> z.timeframe() == tf)
                    .filter(z -> z.createdAt().isAfter(legStart))
                    .filter(z -> z.upper() > close);
            target = selection == TargetSelection.HIGHEST ? candidates.max(byUpper) : candidates.min(byUpper);
            if (target.isPresent()) {
                break;
            }
        }

        if (target.isEmpty()) {
            return new SignalPlan(signal.closeTime(), SignalOutcome.NO_TARGET, close, sl, null, null, null, legStart,
                    Direction.LONG);
        }

        OpenFvg fvg = target.get();
        double rr = (fvg.upper() - close) / (close - sl);
        SignalOutcome outcome = rr >= minRewardToRisk ? SignalOutcome.PLANNED : SignalOutcome.RR_TOO_LOW;
        return new SignalPlan(signal.closeTime(), outcome, close, sl, fvg.upper(), fvg, rr, legStart, Direction.LONG);
    }

    private double atr(List<PriceCandle> candles, int index) {
        double sum = 0;
        for (int i = index - atrPeriod + 1; i <= index; i++) {
            double high = candles.get(i).high().doubleValue();
            double low = candles.get(i).low().doubleValue();
            double prevClose = candles.get(i - 1).close().doubleValue();
            sum += Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
        }
        return sum / atrPeriod;
    }
}
