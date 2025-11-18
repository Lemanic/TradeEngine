package pl.tradeengine.domain.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FvgZone {
    Long id;
    Symbol symbol;
    Timeframe timeframe;
    Direction direction;
    double lowerPrice;
    double upperPrice;
    FvgKind kind; // FVG, IFVG
    FvgStatus status; // OPEN, TOUCHED, FILLED, INVALIDATED

    public FvgZone(Long id, Symbol symbol, Timeframe timeframe, Direction direction, double lowerPrice, double upperPrice, FvgKind kind, FvgStatus status) {
        this.id = id;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.direction = direction;
        this.lowerPrice = lowerPrice;
        this.upperPrice = upperPrice;
        this.kind = kind;
        this.status = status;
    }
}