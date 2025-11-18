package pl.tradeengine.adapter.outbound.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgKind;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.Timeframe;

@Entity
@Getter
@Setter
@Table(name = "fvg_zone")
public class FvgEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;          // np. "BTCUSDT"

    @Enumerated(EnumType.STRING)
    private Timeframe timeframe;

    @Enumerated(EnumType.STRING)
    private Direction direction;    // LONG/SHORT

    private Double lowerPrice;
    private Double upperPrice;

    @Enumerated(EnumType.STRING)
    private FvgKind kind;           // FVG / IFVG

    @Enumerated(EnumType.STRING)
    private FvgStatus status;       // CREATED / TOUCHED / FILLED ...

    // wymagany przez JPA konstruktor bezargumentowy
    protected FvgEntity() {}

    public FvgEntity(Long id,
                     String symbol,
                     Timeframe timeframe,
                     Direction direction,
                     Double lowerPrice,
                     Double upperPrice,
                     FvgKind kind,
                     FvgStatus status) {
        this.id = id;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.direction = direction;
        this.lowerPrice = lowerPrice;
        this.upperPrice = upperPrice;
        this.kind = kind;
        this.status = status;
    }

    // gettery/settery albo record + @Entity jeśli chcesz się pobawić
}
