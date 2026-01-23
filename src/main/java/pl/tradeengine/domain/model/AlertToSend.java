package pl.tradeengine.domain.model;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;

@Getter
public class AlertToSend {
    Symbol symbol;
    Direction direction;
    String scenarioName;
    Timeframe timeframe;
    BigDecimal entryPrice;
    Optional<BigDecimal> stopLoss;
    Optional<BigDecimal> takeProfit;
    String description;
    ZonedDateTime timestamp;

    public AlertToSend(Symbol symbol, Direction direction, String scenarioName, Timeframe timeframe, BigDecimal entryPrice, Optional<BigDecimal> stopLoss, Optional<BigDecimal> takeProfit, String description, ZonedDateTime timestamp) {
        this.symbol = symbol;
        this.direction = direction;
        this.scenarioName = scenarioName;
        this.timeframe = timeframe;
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.description = description;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return description;
    }

}
