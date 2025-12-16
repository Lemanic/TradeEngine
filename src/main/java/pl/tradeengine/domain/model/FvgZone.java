package pl.tradeengine.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Getter
@Setter
public class FvgZone {
    Long id;
    Symbol symbol;
    Timeframe timeframe;
    Direction direction;
    BigDecimal lowerPrice;
    BigDecimal upperPrice;
    Double strength;
    FvgKind kind;
    FvgStatus status;

    AlertMode alertMode;
    ZonedDateTime touchedAt;
    ZonedDateTime leftZoneAt;
    ZonedDateTime filledAt;
    ZonedDateTime expiresAt;

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

        // defaults:
        this.alertMode = AlertMode.OFF;
        this.touchedAt = null;
        this.leftZoneAt = null;
        this.filledAt = null;
        this.expiresAt = null;
    }

    public FvgZone(Long id, Symbol symbol, Timeframe timeframe, Direction direction, BigDecimal lowerPrice, BigDecimal upperPrice, Double strength, FvgKind kind, FvgStatus status, AlertMode alertMode, ZonedDateTime touchedAt, ZonedDateTime leftZoneAt, ZonedDateTime filledAt, ZonedDateTime expiresAt) {
        this(id, symbol, timeframe, direction, lowerPrice, upperPrice, strength, kind, status);
        this.alertMode = alertMode;
        this.touchedAt = touchedAt;
        this.leftZoneAt = leftZoneAt;
        this.filledAt = filledAt;
        this.expiresAt = expiresAt;
    }
}