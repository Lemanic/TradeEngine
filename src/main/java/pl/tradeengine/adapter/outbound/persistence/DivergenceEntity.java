package pl.tradeengine.adapter.outbound.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@Table(name = "divergence_signal")
public class DivergenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;

    @Enumerated(EnumType.STRING)
    private Timeframe timeframe;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    private Double strength;

    private ZonedDateTime detectedAt;

    protected DivergenceEntity() {}

    public DivergenceEntity(Long id,
                            String symbol,
                            Timeframe timeframe,
                            Direction direction,
                            Double strength,
                            ZonedDateTime detectedAt) {
        this.id = id;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.direction = direction;
        this.strength = strength;
        this.detectedAt = detectedAt;
    }
}

