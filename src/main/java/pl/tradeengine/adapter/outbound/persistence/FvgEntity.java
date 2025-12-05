package pl.tradeengine.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgKind;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.Timeframe;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "fvg_zone")
public class FvgEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;

    @Enumerated(EnumType.STRING)
    private Timeframe timeframe;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    @Column(precision = 19, scale = 4)
    private BigDecimal lowerPrice;
    @Column(precision = 19, scale = 4)
    private BigDecimal upperPrice;
    private Double strength;

    @Enumerated(EnumType.STRING)
    private FvgKind kind;

    @Enumerated(EnumType.STRING)
    private FvgStatus status;

    protected FvgEntity() {}

    public FvgEntity(Long id, String symbol, Timeframe timeframe, Direction direction, BigDecimal lowerPrice, BigDecimal upperPrice, Double strength, FvgKind kind, FvgStatus status) {
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
