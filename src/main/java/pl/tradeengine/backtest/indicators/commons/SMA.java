package pl.tradeengine.backtest.indicators.commons;

import java.util.LinkedList;
import java.util.Deque;

public class SMA {

    private final int period;
    private final Deque<Double> buffer = new LinkedList<>();
    private double sum = 0.0;

    public SMA(int period) {
        this.period = period;
    }

    public double next(double value) {
        buffer.addLast(value);
        sum += value;

        if (buffer.size() > period) {
            sum -= buffer.removeFirst();
        }

        return sum / buffer.size();
    }

    public void reset() {
        buffer.clear();
        sum = 0.0;
    }
}
