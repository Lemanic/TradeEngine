package pl.tradeengine.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
public class DivergenceSignal {
    Long id;
    Symbol symbol;
    Timeframe timeframe;
    Direction direction; // BULLISH: long, BEARISH: short
    Double strength;
    ZonedDateTime detectedAt;

    public DivergenceSignal(Long id, Symbol symbol, Timeframe timeframe, Direction direction, Double strength, ZonedDateTime detectedAt) {
        this.id = id;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.direction = direction;
        this.strength = strength;
        this.detectedAt = detectedAt;
    }

}