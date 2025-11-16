package pl.tradeengine.alerts.infra.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pl.tradeengine.alerts.domain.Direction;

@Getter
@Setter
@Entity
@Table(name = "fvg_zone")
public class FvgZoneEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;

    private String timeframe;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    private double strength;

    private double fvgLow;

    private double fvgHigh;

    private boolean active = true;

    @Enumerated(EnumType.STRING)
    private FvgStatus status = FvgStatus.CREATED;

    protected FvgZoneEntity() {}

    public FvgZoneEntity(String symbol, String timeframe, Direction direction, double strength, double fvgLow, double fvgHigh, FvgStatus status) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.direction = direction;
        this.strength = strength;
        this.fvgLow = fvgLow;
        this.fvgHigh = fvgHigh;
        this.active = true;
        this.status = status;
    }

    public FvgStatus getStatus() {
        return status;
    }
}
