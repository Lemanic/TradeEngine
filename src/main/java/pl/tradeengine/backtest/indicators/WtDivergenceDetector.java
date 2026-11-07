package pl.tradeengine.backtest.indicators;

import pl.tradeengine.domain.model.Direction;

import java.util.Optional;

/**
 * Regular WaveTrend divergences, ported 1:1 from VuManChu Cipher B ({@code f_findDivs} on
 * {@code wt2}). Mirrors the indicator's "Divergence buy/sell circle" with its default inputs:
 * WT regular divergences on, the 2nd range on, RSI and Stoch divergences off, hidden off.
 *
 * <p>A divergence is confirmed two bars after its pivot (5-bar fractal), so {@link #next} reports
 * it on the bar on which TradingView would fire the alert, not on the pivot bar where the dot is
 * drawn ({@code offset = -2}).
 */
public class WtDivergenceDetector {

    private final FractalDivergenceFinder primary = new FractalDivergenceFinder(45, -65);
    private final FractalDivergenceFinder secondRange = new FractalDivergenceFinder(15, -40);

    /** Feed one closed bar; returns the direction of a divergence confirmed on this bar. */
    public Optional<Direction> next(double wt2, double high, double low) {
        Optional<Direction> a = primary.next(wt2, high, low);
        Optional<Direction> b = secondRange.next(wt2, high, low);
        return a.isPresent() ? a : b;
    }

    /** One {@code f_findDivs(wt2, topLimit, botLimit, true)} call. */
    static final class FractalDivergenceFinder {

        private final double topLimit;
        private final double botLimit;

        // Last five bars, index 0 = current bar (Pine's src[0]).
        private final double[] src = new double[5];
        private final double[] highs = new double[5];
        private final double[] lows = new double[5];
        private int bars = 0;

        // Pivot of the most recent qualifying fractal, as ta.valuewhen(...) remembers it.
        private Double prevTopSrc;
        private Double prevTopHigh;
        private Double prevBotSrc;
        private Double prevBotLow;

        FractalDivergenceFinder(double topLimit, double botLimit) {
            this.topLimit = topLimit;
            this.botLimit = botLimit;
        }

        Optional<Direction> next(double wt2, double high, double low) {
            shift(src, wt2);
            shift(highs, high);
            shift(lows, low);
            bars++;
            if (bars < 5) {
                return Optional.empty();
            }

            double pivot = src[2];
            boolean top = src[4] < pivot && src[3] < pivot && pivot > src[1] && pivot > src[0]
                    && pivot >= topLimit;
            boolean bot = src[4] > pivot && src[3] > pivot && pivot < src[1] && pivot < src[0]
                    && pivot <= botLimit;
            // In Pine the fractal series is the value itself, so a pivot of exactly 0 reads as false.
            top &= pivot != 0;
            bot &= pivot != 0;

            Direction result = null;

            // Two fractals of one kind are at least 3 bars apart, so the [2] offset on
            // ta.valuewhen in Pine always resolves to the previous fractal, never the current one.
            if (top) {
                if (prevTopSrc != null && highs[2] > prevTopHigh && pivot < prevTopSrc) {
                    result = Direction.SHORT;
                }
                prevTopSrc = pivot;
                prevTopHigh = highs[2];
            }
            if (bot) {
                if (prevBotSrc != null && lows[2] < prevBotLow && pivot > prevBotSrc) {
                    result = Direction.LONG;
                }
                prevBotSrc = pivot;
                prevBotLow = lows[2];
            }
            return Optional.ofNullable(result);
        }

        private static void shift(double[] window, double value) {
            System.arraycopy(window, 0, window, 1, window.length - 1);
            window[0] = value;
        }
    }
}
