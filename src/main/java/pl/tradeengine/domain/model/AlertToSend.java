package pl.tradeengine.domain.model;

import java.util.Optional;

public class AlertToSend {
    Symbol symbol;
    Direction direction;
    String scenarioName;
    Timeframe entryTimeframe;
    double entryPrice;
    Optional<Double> stopLoss;
    Optional<Double> takeProfit;
    String description; // np. "Touch key level H4 + bullish divergence M15"

    public AlertToSend(Symbol symbol, Direction direction, String scenarioName, Timeframe entryTimeframe, double entryPrice, Optional<Double> stopLoss, Optional<Double> takeProfit, String description) {
        this.symbol = symbol;
        this.direction = direction;
        this.scenarioName = scenarioName;
        this.entryTimeframe = entryTimeframe;
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.description = description;
    }
}
