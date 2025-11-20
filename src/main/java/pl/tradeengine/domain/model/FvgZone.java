package pl.tradeengine.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class FvgZone {
    Long id;
    Symbol symbol;
    Timeframe timeframe;
    Direction direction;
    BigDecimal lowerPrice; // <-- ZMIEŃ TYP
    BigDecimal upperPrice; // <-- ZMIEŃ TYP
    Double strength;
    FvgKind kind;
    FvgStatus status;

    public FvgZone(Long id, Symbol symbol, Timeframe timeframe, Direction direction, BigDecimal lowerPrice, BigDecimal upperPrice, Double strength, FvgKind kind, FvgStatus status) {
        this.id = id;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.direction = direction;
        this.lowerPrice = lowerPrice;
        this.upperPrice = upperPrice;
        this.strength = strength;
        this.kind = kind;
        this.status = status;
    }

}