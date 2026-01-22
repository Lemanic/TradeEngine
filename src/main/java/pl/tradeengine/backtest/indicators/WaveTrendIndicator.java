package pl.tradeengine.backtest.indicators;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.backtest.indicators.commons.EMA;
import pl.tradeengine.backtest.indicators.commons.SMA;

import java.math.BigDecimal;

@Slf4j
public class WaveTrendIndicator {

    private final int channelLen;
    private final int averageLen;
    private final int maLen;

    private final EMA esaEMA;
    private final EMA deEMA;
    private final EMA wt1EMA;
    private final SMA wt2SMA;

    private Double previousWt1 = null;
    private Double previousWt2 = null;

    private int callCount = 0;

    public WaveTrendIndicator(int channelLen, int averageLen, int maLen) {
        this.channelLen = channelLen;
        this.averageLen = averageLen;
        this.maLen = maLen;

        this.esaEMA = new EMA(channelLen);
        this.deEMA = new EMA(channelLen);
        this.wt1EMA = new EMA(averageLen);
        this.wt2SMA = new SMA(maLen);
    }
    public WaveTrendResult next(BigDecimal hlc3Price) {
        double src = hlc3Price.doubleValue();

        // 1. ESA = EMA(src, channelLen)
        double esa = esaEMA.next(src);

        // 2. DE = EMA(|src - esa|, channelLen)
        double de = deEMA.next(Math.abs(src - esa));

        // 3. CI = (src - esa) / (0.015 * de)
        // Ochrona przed dzieleniem przez 0
        double ci;
        if (de == 0.0 || Double.isNaN(de)) {
            ci = 0.0;  // ← Jeśli de=0, to ci=0 (brak zmiany)
        } else {
            ci = (src - esa) / (0.015 * de);
        }

        // 4. WT1 = EMA(ci, averageLen)
        double wt1 = wt1EMA.next(ci);

        // 5. WT2 = SMA(wt1, maLen)
        double wt2 = wt2SMA.next(wt1);

        // DEBUG: Log first 20 calls
        callCount++;
        if (callCount <= 20) {
            log.info("WT call #{}: src={}, esa={}, de={}, ci={}, wt1={}, wt2={}",
                    callCount, src, esa, de, ci, wt1, wt2);
        }

        // 6. Detect Cross
        boolean cross = false;
        boolean crossUp = false;
        boolean crossDown = false;

        if (previousWt1 != null && previousWt2 != null) {
            boolean wasBelowNowAbove = (previousWt1 <= previousWt2) && (wt1 > wt2);
            boolean wasAboveNowBelow = (previousWt1 >= previousWt2) && (wt1 < wt2);

            cross = wasBelowNowAbove || wasAboveNowBelow;
            crossUp = wasBelowNowAbove;
            crossDown = wasAboveNowBelow;

            if (cross && callCount <= 100) {
                log.info("  🎯 CROSS DETECTED at call #{}: wt1={}, wt2={}, crossUp={}",
                        callCount, wt1, wt2, crossUp);
            }
        }

        previousWt1 = wt1;
        previousWt2 = wt2;

        return new WaveTrendResult(wt1, wt2, cross, crossUp, crossDown);
    }


    public record WaveTrendResult(
            double wt1,
            double wt2,
            boolean cross,
            boolean crossUp,
            boolean crossDown
    ) {}
}
