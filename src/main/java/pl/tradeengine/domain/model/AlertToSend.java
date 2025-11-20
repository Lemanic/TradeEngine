package pl.tradeengine.domain.model;

import java.math.BigDecimal;
import java.util.Optional;

public class AlertToSend {
    Symbol symbol;
    Direction direction;
    String scenarioName;
    Timeframe timeframe;
    BigDecimal entryPrice;
    Optional<Double> stopLoss;
    Optional<Double> takeProfit;
    String description;

    public AlertToSend(Symbol symbol, Direction direction, String scenarioName, Timeframe timeframe, BigDecimal entryPrice, Optional<Double> stopLoss, Optional<Double> takeProfit, String description) {
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
                "ALERT! %s, %s, %s, TF=%s",
                description, symbol.code(), direction, timeframe
        );
    }

}
