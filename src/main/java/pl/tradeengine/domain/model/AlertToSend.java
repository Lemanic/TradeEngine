package pl.tradeengine.domain.model;

import java.util.Optional;

public class AlertToSend {
    Symbol symbol;
    Direction direction;
    String scenarioName;
    Timeframe timeframe;
    double entryPrice;
    Optional<Double> stopLoss;
    Optional<Double> takeProfit;
    String description;

    public AlertToSend(Symbol symbol, Direction direction, String scenarioName, Timeframe timeframe, double entryPrice, Optional<Double> stopLoss, Optional<Double> takeProfit, String description) {
        this.symbol = symbol;
        this.direction = direction;
        this.scenarioName = scenarioName;
        this.timeframe = timeframe;
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.description = description;
    }

    @Override
    public String toString() {
        return String.format(
                "Alert[%s, %s, %s, TF=%s, Entry=%.2f, Desc=%s]",
                scenarioName, symbol.code(), direction, timeframe, entryPrice, description
        );
    }

}
