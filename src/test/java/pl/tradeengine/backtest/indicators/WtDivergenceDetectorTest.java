package pl.tradeengine.backtest.indicators;

import org.junit.jupiter.api.Test;
import pl.tradeengine.domain.model.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WtDivergenceDetectorTest {

    private static List<Optional<Direction>> feed(double[] wt2, double[] high, double[] low) {
        WtDivergenceDetector detector = new WtDivergenceDetector();
        List<Optional<Direction>> out = new ArrayList<>();
        for (int i = 0; i < wt2.length; i++) {
            out.add(detector.next(wt2[i], high[i], low[i]));
        }
        return out;
    }

    private static double[] flat(int n, double v) {
        double[] a = new double[n];
        java.util.Arrays.fill(a, v);
        return a;
    }

    @Test
    void bearishDivergence_isReportedTwoBarsAfterThePivot() {
        // Pivots on bars 2 and 7: higher price high, lower wt2 — regular bearish divergence.
        double[] wt2 = {0, 10, 60, 10, 0, 0, 10, 50, 10, 0};
        double[] high = flat(10, 50);
        high[2] = 100;
        high[7] = 110;

        List<Optional<Direction>> out = feed(wt2, high, flat(10, 40));

        assertThat(out.get(9)).contains(Direction.SHORT);
        assertThat(out.subList(0, 9)).allMatch(Optional::isEmpty);
    }

    @Test
    void bullishDivergence_mirrorsTheBearishCase() {
        double[] wt2 = {0, -10, -70, -10, 0, 0, -10, -50, -10, 0};
        double[] low = flat(10, 50);
        low[2] = 20;
        low[7] = 10;

        assertThat(feed(wt2, flat(10, 60), low).get(9)).contains(Direction.LONG);
    }

    @Test
    void noDivergence_whenOscillatorConfirmsTheNewHigh() {
        double[] wt2 = {0, 10, 50, 10, 0, 0, 10, 60, 10, 0};
        double[] high = flat(10, 50);
        high[2] = 100;
        high[7] = 110;

        assertThat(feed(wt2, high, flat(10, 40))).allMatch(Optional::isEmpty);
    }

    @Test
    void topBelowEveryLimit_isNotAFractalAtAll() {
        // 10 is under both the 45 and the 15 bearish limits, so neither pivot qualifies.
        double[] wt2 = {0, 5, 12, 5, 0, 0, 5, 10, 5, 0};
        double[] high = flat(10, 50);
        high[2] = 100;
        high[7] = 110;

        assertThat(feed(wt2, high, flat(10, 40))).allMatch(Optional::isEmpty);
    }
}
