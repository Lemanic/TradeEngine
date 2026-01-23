package pl.tradeengine.backtest.indicators.commons;

public class EMA {

    private final int period;
    private final double multiplier;
    private Double previousEMA = null;

    public EMA(int period) {
        this.period = period;
        this.multiplier = 2.0 / (period + 1);
    }

    public double next(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            value = 0.0;
        }

        if (previousEMA == null) {
            previousEMA = value;
            return value;
        }

        double ema = (value - previousEMA) * multiplier + previousEMA;
        previousEMA = ema;
        return ema;
    }

    public void reset() {
        previousEMA = null;
    }
}
